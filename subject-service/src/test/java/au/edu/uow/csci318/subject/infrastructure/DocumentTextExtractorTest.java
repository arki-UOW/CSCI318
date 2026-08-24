package au.edu.uow.csci318.subject.infrastructure;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTextExtractorTest {
    @Test
    void extractsParagraphsAndTablesFromDocxBeforeAiProcessing() throws Exception {
        byte[] documentBytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Subject Code: CSCI318");
            document.createParagraph().createRun().setText("Subject Name: Software Engineering");
            var table = document.createTable(2, 3);
            table.getRow(0).getCell(0).setText("Assessment");
            table.getRow(0).getCell(1).setText("Weight");
            table.getRow(0).getCell(2).setText("Due");
            table.getRow(1).getCell(0).setText("Architecture report");
            table.getRow(1).getCell(1).setText("30%");
            table.getRow(1).getCell(2).setText("Week 8");
            document.write(output);
            documentBytes = output.toByteArray();
        }

        DocumentTextExtractor extractor = new DocumentTextExtractor(new PdfTextExtractor(), "tesseract");
        String text = extractor.extract(new MockMultipartFile(
                "file", "outline.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", documentBytes));

        assertTrue(text.contains("Subject Code: CSCI318"));
        assertTrue(text.contains("Architecture report | 30% | Week 8"));
    }
}
