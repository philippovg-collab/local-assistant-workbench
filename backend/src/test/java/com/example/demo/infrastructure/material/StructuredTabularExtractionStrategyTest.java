package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class StructuredTabularExtractionStrategyTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor(
        new MaterialProperties(),
        new MaterialFormatRegistry()
    );

    @Test
    void extractsTableBlocksFromCsv() {
        DocumentParseResult result = extractor.extract(
            "rates.csv",
            "text/csv",
            """
                service,price
                reserve,12000
                backup,9500
                """.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(DocumentParserProfile.TABULAR, result.parserProfile());
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE));
    }

    @Test
    void extractsTableBlocksFromXlsx() throws Exception {
        DocumentParseResult result = extractor.extract(
            "rates.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            createWorkbook()
        );

        assertEquals(DocumentParserProfile.TABULAR, result.parserProfile());
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE));
        assertTrue(result.blocks().stream().anyMatch(block -> block.text().contains("Service")));
    }

    private byte[] createWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Rates");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Service");
            header.createCell(1).setCellValue("Price");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Reserve");
            row.createCell(1).setCellValue(12000);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
