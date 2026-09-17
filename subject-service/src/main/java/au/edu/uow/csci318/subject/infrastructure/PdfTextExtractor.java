package au.edu.uow.csci318.subject.infrastructure;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor {
  String extract(byte[] bytes) {
    try (var document = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      stripper.setLineSeparator("\n");
      return DocumentTextExtractor.normalise(stripper.getText(document));
    } catch (IOException e) {
      throw new IllegalArgumentException("The PDF could not be read", e);
    }
  }
}
