package com.example.demo.infrastructure.material;

import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialFormatRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.Test;

class StructuredPresentationExtractionStrategyTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor(
        new MaterialProperties(),
        new MaterialFormatRegistry()
    );

    @Test
    void extractsSlideBlocksFromPptx() throws Exception {
        DocumentParseResult result = extractor.extract(
            "deck.pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            createPresentation()
        );

        assertEquals(DocumentParserProfile.PRESENTATION, result.parserProfile());
        assertTrue(result.blocks().stream().anyMatch(block -> block.text().contains("Grid upgrade deck")));
        assertTrue(result.blocks().stream().anyMatch(block -> block.text().contains("Timeline")));
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TITLE || block.type() == DocumentBlockType.SLIDE));
    }

    private byte[] createPresentation() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();

            XSLFTextBox title = slide.createTextBox();
            title.setAnchor(new Rectangle2D.Double(40, 30, 400, 50));
            title.setText("Grid upgrade deck");

            XSLFTextBox body = slide.createTextBox();
            body.setAnchor(new Rectangle2D.Double(40, 100, 400, 200));
            XSLFTextParagraph firstParagraph = body.addNewTextParagraph();
            firstParagraph.setBullet(true);
            XSLFTextRun firstRun = firstParagraph.addNewTextRun();
            firstRun.setText("Timeline");

            XSLFTextParagraph secondParagraph = body.addNewTextParagraph();
            secondParagraph.setBullet(true);
            XSLFTextRun secondRun = secondParagraph.addNewTextRun();
            secondRun.setText("Capacity reserve");

            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
