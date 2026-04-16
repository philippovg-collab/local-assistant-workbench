package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import com.example.demo.config.OcrProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PdfDocumentExtractionStrategy implements DocumentTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PdfDocumentExtractionStrategy.class);

    private final MaterialFormatRegistry formatRegistry;
    private final OcrProperties ocrProperties;
    private final OcrClient ocrClient;
    private final OcrCapabilityProvider ocrCapabilityProvider;

    public PdfDocumentExtractionStrategy(
        MaterialFormatRegistry formatRegistry,
        OcrProperties ocrProperties,
        OcrClient ocrClient,
        OcrCapabilityProvider ocrCapabilityProvider
    ) {
        this.formatRegistry = formatRegistry;
        this.ocrProperties = ocrProperties;
        this.ocrClient = ocrClient;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
    }

    @Override
    public boolean supports(String originalFileName, String mediaType) {
        String extension = formatRegistry.extensionOf(originalFileName);
        return formatRegistry.isPdfExtension(extension)
            || "application/pdf".equalsIgnoreCase(mediaType);
    }

    @Override
    public ExtractedDocument extract(String originalFileName, String mediaType, byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            int pageCount = document.getNumberOfPages();
            List<ExtractedDocumentSegment> extractedPages = extractEmbeddedText(document, pageCount);
            List<ExtractedDocumentSegment> resolvedPages = new ArrayList<>();
            boolean usedOcr = false;

            if (requiresOcr(extractedPages)) {
                OcrCapability capability = ocrCapabilityProvider.currentCapability();
                if (!capability.scannedPdfSupport()) {
                    logger.warn(
                        "Rejecting scanned PDF before OCR page rendering because OCR capability is unavailable: code={} message={}",
                        capability.reasonCode(),
                        capability.reasonMessage()
                    );
                    throw unavailableOcrException(capability);
                }

                if (pageCount > capability.maxPages()) {
                    throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "material.ocr_page_limit_exceeded",
                        "PDF has " + pageCount + " pages, exceeding the OCR limit of " + capability.maxPages()
                    );
                }

                PDFRenderer renderer = new PDFRenderer(document);
                for (ExtractedDocumentSegment page : extractedPages) {
                    if (meaningfulLength(page.text()) >= ocrProperties.getMinTextThreshold()) {
                        resolvedPages.add(page);
                        continue;
                    }

                    resolvedPages.add(extractPageWithOcr(document, renderer, page.page()));
                    usedOcr = true;
                }
            } else {
                resolvedPages.addAll(extractedPages);
            }

            List<ExtractedDocumentSegment> nonEmptyPages = resolvedPages.stream()
                .filter(page -> StringUtils.hasText(page.text()))
                .sorted(Comparator.comparing(page -> page.page() == null ? 0 : page.page()))
                .toList();

            if (nonEmptyPages.isEmpty()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "material.extraction_failed",
                    "Unable to extract readable text from the uploaded PDF"
                );
            }

            return new ExtractedDocument(
                nonEmptyPages,
                usedOcr ? "pdfbox+tesseract" : "pdfbox",
                usedOcr,
                pageCount
            );
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_failed",
                "Unable to read PDF document",
                exception
            );
        }
    }

    private List<ExtractedDocumentSegment> extractEmbeddedText(PDDocument document, int pageCount) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        List<ExtractedDocumentSegment> pages = new ArrayList<>();

        for (int page = 1; page <= pageCount; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(new ExtractedDocumentSegment(
                stripper.getText(document),
                page,
                "pdfbox",
                false
            ));
        }

        return pages;
    }

    private boolean requiresOcr(List<ExtractedDocumentSegment> pages) {
        return pages.stream()
            .anyMatch(page -> meaningfulLength(page.text()) < ocrProperties.getMinTextThreshold());
    }

    private ExtractedDocumentSegment extractPageWithOcr(
        PDDocument document,
        PDFRenderer renderer,
        Integer pageNumber
    ) throws IOException {
        if (pageNumber == null) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.extraction_failed",
                "PDF page number is missing for OCR fallback"
            );
        }

        PDPage page = document.getPage(pageNumber - 1);
        long estimatedRenderedImageBytes = estimateRenderedImageBytes(page);
        if (estimatedRenderedImageBytes > ocrProperties.getMaxRenderedImageBytes()) {
            logger.warn(
                "Rejecting OCR render for PDF page {} because estimated image footprint {} exceeds limit {}",
                pageNumber,
                estimatedRenderedImageBytes,
                ocrProperties.getMaxRenderedImageBytes()
            );
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.ocr_render_budget_exceeded",
                "Rendered OCR image for PDF page " + pageNumber
                    + " would exceed the pre-render memory budget of " + ocrProperties.getMaxRenderedImageBytes() + " bytes"
            );
        }

        Path imagePath = null;
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, ocrProperties.getRenderDpi(), ImageType.GRAY);
            imagePath = Files.createTempFile("material-pdf-page-" + pageNumber + "-", ".png");
            ImageIO.write(image, "png", imagePath.toFile());

            if (Files.size(imagePath) > ocrProperties.getMaxTempFileBytes()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "material.ocr_temp_file_too_large",
                    "Rendered OCR image for PDF page " + pageNumber + " exceeds the temporary file size limit"
                );
            }

            return new ExtractedDocumentSegment(
                ocrClient.extract(imagePath, pageNumber),
                pageNumber,
                "tesseract",
                true
            );
        } finally {
            if (imagePath != null) {
                Files.deleteIfExists(imagePath);
            }
        }
    }

    private ApiException unavailableOcrException(OcrCapability capability) {
        String code = StringUtils.hasText(capability.reasonCode())
            ? capability.reasonCode()
            : "material.ocr_unavailable";
        String message = StringUtils.hasText(capability.reasonMessage())
            ? capability.reasonMessage()
            : "OCR is unavailable for scanned PDF processing";
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private long estimateRenderedImageBytes(PDPage page) {
        PDRectangle cropBox = page.getCropBox();
        long widthPixels = pixelsAtConfiguredDpi(cropBox.getWidth());
        long heightPixels = pixelsAtConfiguredDpi(cropBox.getHeight());

        try {
            return Math.multiplyExact(widthPixels, heightPixels);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private long pixelsAtConfiguredDpi(float points) {
        return Math.max(1L, (long) Math.ceil(points * ocrProperties.getRenderDpi() / 72.0d));
    }

    private int meaningfulLength(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }

        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "")
            .length();
    }
}
