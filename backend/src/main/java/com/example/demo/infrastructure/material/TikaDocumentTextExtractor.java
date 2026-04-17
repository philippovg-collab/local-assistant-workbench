package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractionStrategy {

    private final MaterialProperties properties;
    private final MaterialFormatRegistry formatRegistry;

    public TikaDocumentTextExtractor(
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry
    ) {
        this.properties = properties;
        this.formatRegistry = formatRegistry;
    }

    @Override
    public boolean supports(String originalFileName, String mediaType) {
        String extension = formatRegistry.extensionOf(originalFileName);
        return formatRegistry.isRichDocumentExtension(extension) && !formatRegistry.isPdfExtension(extension);
    }

    @Override
    public ExtractedDocument extract(String originalFileName, String mediaType, byte[] bytes) {
        return new ExtractedDocument(
            List.of(new ExtractedDocumentSegment(extractRichDocument(originalFileName, bytes), null, "tika", false)),
            "tika",
            false,
            null,
            null,
            null
        );
    }

    private String extractRichDocument(String originalFileName, byte[] bytes) {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ExtractionThreadFactory());

        try {
            var task = executor.submit(() -> parseWithTika(originalFileName, bytes));
            return task.get(properties.getExtractionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_timeout",
                "Document extraction timed out",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.extraction_interrupted",
                "Document extraction was interrupted",
                exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_failed",
                "Unable to extract text from the uploaded file",
                cause
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private String parseWithTika(String originalFileName, byte[] bytes) throws Exception {
        Tika tika = new Tika();
        tika.setMaxStringLength(properties.getMaxTextChars());

        Metadata metadata = new Metadata();
        if (StringUtils.hasText(originalFileName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFileName);
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            return tika.parseToString(inputStream, metadata);
        }
    }

    private static final class ExtractionThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "material-extraction");
            thread.setDaemon(true);
            return thread;
        }
    }
}
