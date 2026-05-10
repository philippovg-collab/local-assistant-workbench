package com.example.demo.infrastructure.material;

import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialFormatRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ApplicationException;
import com.example.demo.config.MaterialProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class StructuredRichDocumentExtractionStrategyTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor(
        new MaterialProperties(),
        new MaterialFormatRegistry()
    );

    @Test
    void extractsStructuredBlocksFromDocx() throws Exception {
        DocumentParseResult result = extractor.extract(
            "policy.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createDocx()
        );

        assertEquals(DocumentParserProfile.RICH_TEXT, result.parserProfile());
        assertTrue(result.blocks().size() >= 2);
        assertTrue(result.blocks().stream().anyMatch(block -> block.text().contains("Grid policy")));
        assertTrue(result.blocks().stream().anyMatch(block -> block.text().contains("Регламент резервирования мощности")));
    }

    @Test
    void mapsHtmlHeadingsListsAndTablesToTypedBlocks() {
        DocumentParseResult result = extractor.extract(
            "report.html",
            "text/html",
            """
                <html>
                  <body>
                    <h1>Grid Policy</h1>
                    <p>Регламент резервирования мощности.</p>
                    <ul>
                      <li>Подготовить схему</li>
                      <li>Согласовать лимиты</li>
                    </ul>
                    <table>
                      <tr><th>Параметр</th><th>Значение</th></tr>
                      <tr><td>Мощность</td><td>120 МВт</td></tr>
                    </table>
                  </body>
                </html>
                """.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(DocumentParserProfile.RICH_TEXT, result.parserProfile());
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TITLE));
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.LIST));
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE));
    }

    @Test
    void rejectsDocumentsWhenTikaOutputExceedsWriteLimit() {
        MaterialProperties properties = new MaterialProperties();
        properties.setTikaWriteLimitChars(64);
        TikaDocumentTextExtractor boundedExtractor = new TikaDocumentTextExtractor(
            properties,
            new MaterialFormatRegistry()
        );

        ApplicationException exception = assertThrows(ApplicationException.class, () -> boundedExtractor.extract(
            "large.html",
            "text/html",
            ("<html><body><p>" + "long content ".repeat(100) + "</p></body></html>")
                .getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals("material.extraction_too_large", exception.getCode());
    }

    private byte[] createDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var title = document.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText("Grid policy");

            document.createParagraph()
                .createRun()
                .setText("Регламент резервирования мощности действует до конца 2026 года.");

            document.createParagraph()
                .createRun()
                .setText("Подразделение готовит схему переключений.");

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
