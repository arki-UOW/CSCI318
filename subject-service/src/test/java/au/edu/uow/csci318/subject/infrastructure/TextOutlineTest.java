package au.edu.uow.csci318.subject.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TextOutlineTest {
  private final DocumentTextExtractor reader = new DocumentTextExtractor(mock(PdfTextExtractor.class), "tesseract");
  @Test void acceptsUtf8TextWithBomAndCaseInsensitiveExtension() {
    var file = new MockMultipartFile("file", "outline.TXT", "text/plain",
        "\uFEFFSubject Code: CSCI318\nSubject Name: Software Engineering\nAssessment: Report 30%".getBytes(StandardCharsets.UTF_8));
    String text = reader.extract(file);
    assertTrue(text.startsWith("Subject Code: CSCI318"));
    assertTrue(text.contains("Assessment: Report 30%"));
  }
  @Test void rejectsBinaryOrInvalidUtf8DisguisedAsText() {
    assertThrows(IllegalArgumentException.class, () -> reader.extract(new MockMultipartFile("file", "outline.txt", "text/plain", new byte[]{(byte)0xff, (byte)0xff})));
    assertThrows(IllegalArgumentException.class, () -> reader.extract(new MockMultipartFile("file", "outline.txt", "text/plain", "Subject Code: CSCI318\u0000 invalid binary data".getBytes(StandardCharsets.UTF_8))));
  }
}
