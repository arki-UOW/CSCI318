package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.application.SubjectDocumentReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class DocumentTextExtractor implements SubjectDocumentReader {
  static final long MAX_FILE_BYTES = 15_000_000;
  static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "jpg", "jpeg");

  private final PdfTextExtractor pdf;
  private final String tesseractCommand;

  DocumentTextExtractor(
      PdfTextExtractor pdf, @Value("${study.ocr.command:tesseract}") String tesseractCommand) {
    this.pdf = pdf;
    this.tesseractCommand = tesseractCommand;
  }

  public String extract(MultipartFile file) {
    validate(file);
    String extension = extension(file.getOriginalFilename());
    try {
      byte[] bytes = file.getBytes();
      String text =
          switch (extension) {
            case "pdf" -> pdf.extract(bytes);
            case "docx" -> extractDocx(bytes);
            case "jpg", "jpeg" -> extractImage(bytes, extension);
            default -> throw new IllegalArgumentException("Unsupported document type");
          };
      String normalised = normalise(text);
      if (normalised.length() < 20) {
        throw new IllegalArgumentException(
            "Very little readable text was found. Upload a clearer document or enter the subject"
                + " manually.");
      }
      return normalised;
    } catch (IOException exception) {
      throw new IllegalArgumentException("The uploaded document could not be read", exception);
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Choose a non-empty PDF, DOCX, JPG or JPEG file");
    }
    if (file.getOriginalFilename() == null
        || !SUPPORTED_EXTENSIONS.contains(extension(file.getOriginalFilename()))) {
      throw new IllegalArgumentException(
          "Only PDF, DOCX, JPG and JPEG subject outlines are supported");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new IllegalArgumentException("The document must be 15 MB or smaller");
    }
  }

  private String extractDocx(byte[] bytes) throws IOException {
    StringBuilder text = new StringBuilder();
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      document.getParagraphs().forEach(paragraph -> appendLine(text, paragraph.getText()));
      for (XWPFTable table : document.getTables()) {
        table
            .getRows()
            .forEach(
                row ->
                    appendLine(
                        text,
                        String.join(
                            " | ",
                            row.getTableCells().stream()
                                .map(cell -> cell.getText().trim())
                                .toList())));
      }
      document.getHeaderList().forEach(header -> appendLine(text, header.getText()));
      document.getFooterList().forEach(footer -> appendLine(text, footer.getText()));
    }
    return text.toString();
  }

  private String extractImage(byte[] bytes, String extension) throws IOException {
    if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
      throw new IllegalArgumentException("The selected image is not a readable JPG or JPEG");
    }
    Path temporaryImage = Files.createTempFile("study-leftovers-ocr-", "." + extension);
    try {
      Files.write(temporaryImage, bytes);
      Process process =
          new ProcessBuilder(
                  List.of(
                      tesseractCommand,
                      temporaryImage.toString(),
                      "stdout",
                      "-l",
                      "eng",
                      "--psm",
                      "6"))
              .redirectErrorStream(true)
              .start();
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var output =
            executor.submit(
                () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        if (!process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS)) {
          process.destroyForcibly();
          throw new IllegalArgumentException(
              "Image text extraction timed out. Try a clearer image.");
        }
        String text = output.get(5, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
          throw new IllegalArgumentException(
              "Image text extraction is unavailable. Enter the subject manually or restart the"
                  + " rebuilt service.");
        }
        return text;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalArgumentException("Image text extraction was interrupted", exception);
      } catch (java.util.concurrent.ExecutionException
          | java.util.concurrent.TimeoutException exception) {
        throw new IllegalArgumentException("Image text extraction did not complete", exception);
      }
    } finally {
      Files.deleteIfExists(temporaryImage);
    }
  }

  private static void appendLine(StringBuilder target, String value) {
    if (value != null && !value.isBlank()) {
      target.append(value.trim()).append('\n');
    }
  }

  private static String extension(String filename) {
    int dot = filename == null ? -1 : filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static String normalise(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
        .replace('\u00a0', ' ')
        .replace("\u00ad", "")
        .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
        .replaceAll("(?m)^ +| +$", "")
        .replaceAll("\n{3,}", "\n\n")
        .trim();
  }
}
