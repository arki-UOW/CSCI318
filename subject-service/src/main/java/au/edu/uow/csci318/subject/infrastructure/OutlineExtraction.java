package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.dto.SubjectDtos.AssessmentCandidate;
import au.edu.uow.csci318.subject.dto.SubjectDtos.AiStatus;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface OutlineExtraction {
    ExtractionResult extract(String extractedText);
}

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

@Component
class ConfiguredChatModel {
    record Selection(String provider, String modelName, ChatModel model) {
    }

    private final String provider;
    private final String geminiKey;
    private final String geminiModel;
    private final String openAiKey;
    private final String openAiModel;

    ConfiguredChatModel(
            @Value("${study.ai.provider:auto}") String provider,
            @Value("${study.ai.gemini.api-key:}") String geminiKey,
            @Value("${study.ai.gemini.model:gemini-3.6-flash}") String geminiModel,
            @Value("${study.ai.openai.api-key:}") String openAiKey,
            @Value("${study.ai.openai.model:gpt-4.1-mini}") String openAiModel) {
        this.provider = provider == null ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
        this.geminiKey = geminiKey == null ? "" : geminiKey.trim();
        this.geminiModel = geminiModel;
        this.openAiKey = openAiKey == null ? "" : openAiKey.trim();
        this.openAiModel = openAiModel;
    }

    Optional<Selection> selection() {
        if (!Set.of("auto", "gemini", "openai").contains(provider)) {
            throw new IllegalArgumentException("AI_PROVIDER must be auto, gemini, or openai");
        }
        if ((provider.equals("auto") || provider.equals("gemini")) && !geminiKey.isBlank()) {
            ChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiKey)
                    .modelName(geminiModel)
                    .temperature(0.0)
                    .responseFormat(ResponseFormat.JSON)
                    .build();
            return Optional.of(new Selection("Gemini", geminiModel, model));
        }
        if ((provider.equals("auto") || provider.equals("openai")) && !openAiKey.isBlank()) {
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName(openAiModel)
                    .temperature(0.0)
                    .build();
            return Optional.of(new Selection("OpenAI", openAiModel, model));
        }
        return Optional.empty();
    }

    String missingConfigurationMessage() {
        return switch (provider) {
            case "gemini" -> "Gemini is selected but GEMINI_API_KEY is not configured";
            case "openai" -> "OpenAI is selected but OPENAI_API_KEY is not configured";
            default -> "No AI API key is configured";
        };
    }

    AiStatus status() {
        Optional<Selection> selected = selection();
        if (selected.isPresent()) {
            return new AiStatus(selected.get().provider(), selected.get().modelName(), true,
                    selected.get().provider() + " is configured in the running Subject Service");
        }
        String model = provider.equals("openai") ? openAiModel : geminiModel;
        String label = provider.equals("openai") ? "OpenAI" : "Gemini";
        return new AiStatus(label, model, false, missingConfigurationMessage());
    }
}

@Component
class SafeOutlineExtractor implements OutlineExtraction {
    private static final int MAX_TEXT_LENGTH = 50_000;
    private static final Pattern SUBJECT_CODE = Pattern.compile("\\b([A-Z]{2,8}\\s?[0-9]{3,4})\\b");
    private static final Pattern LABELLED_NAME = Pattern.compile(
            "(?im)^(?:subject|course)\\s*(?:name|title)\\s*[:\\-]\\s*(.{3,160})$");
    private static final Pattern CREDIT_POINTS = Pattern.compile(
            "(?i)credit\\s*points?\\s*[:\\-]?\\s*(\\d{1,2})");
    private static final Pattern WEIGHT = Pattern.compile("(?<!\\d)(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final Pattern DUE_WEEK = Pattern.compile("(?i)\\bweek\\s*(\\d{1,2})\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2}-\\d{1,2}-\\d{1,2})\\b");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");
    private static final Pattern NAMED_DATE = Pattern.compile(
            "(?i)\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)?\\s*"
                    + "(\\d{1,2}(?:st|nd|rd|th)?\\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+20\\d{2})\\b");
    private static final Pattern NON_ASSESSMENT_TITLE = Pattern.compile(
            "(?i).*(learning outcome|\\bslo\\d*\\b|eligible for a pass|submitted late|late submission|"
                    + "academic integrity|marking criteria|assessment summary|name\\s+type|weighting|"
                    + "contact details|student must|policy|penalt(?:y|ies)).*");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-M-uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-M-uu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH));

    private static final String EXTRACTION_PROMPT = """
            You extract factual academic-planning data from text produced by a university subject-outline
            document extractor. The source may have been PDF, DOCX or OCR text from a JPG. Return ONLY one JSON object with:
            subjectCode, subjectName, creditPoints, assessments, warnings.
            assessments is an array of objects with title, type, weighting, dueDate, dueWeek, description,
            estimatedHours, confidence, warning.

            Rules:
            - Use ISO yyyy-MM-dd for an exact dueDate.
            - Preserve a stated teaching week in dueWeek. Never invent a calendar date from a week number.
            - Use null when a value is absent or uncertain; do not guess.
            - Associate wrapped table cells with the correct assessment row.
            - Weighting is a number from 0 to 100, without the percent sign.
            - confidence is a number from 0 to 1.
            - Do not treat learning outcomes, weekly topics, policies, or contact details as assessments.
            - Do not use table headings, eligibility requirements, late-submission rules or SLO mappings as titles.
            - Include an assessment only when the text explicitly identifies a real task, exam, quiz, project,
              report, presentation, laboratory, portfolio or test.
            - Add a concise warning for every ambiguous or missing planning field.
            """;

    private final ObjectMapper json;
    private final ConfiguredChatModel configuredModel;

    SafeOutlineExtractor(ObjectMapper json, ConfiguredChatModel configuredModel) {
        this.json = json;
        this.configuredModel = configuredModel;
    }

    @Override
    public ExtractionResult extract(String extractedText) {
        String text = extractedText == null ? "" : extractedText.trim();
        Optional<ConfiguredChatModel.Selection> selection = configuredModel.selection();
        if (selection.isPresent()) {
            try {
                return validate(ai(selection.get(), text));
            } catch (Exception e) {
                throw new IllegalArgumentException(providerFailure(selection.get(), e), e);
            }
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "No readable text could be extracted from this document. Upload a clearer file or enter the subject manually.");
        }
        return withWarning(deterministic(text),
                configuredModel.missingConfigurationMessage() + "; deterministic extraction was used.");
    }

    static String providerFailure(ConfiguredChatModel.Selection selection, Exception failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(' ').append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }
        String details = messages.toString();
        String prefix = selection.provider() + " could not analyse this document. ";
        if (details.contains("429") || details.contains("quota") || details.contains("resource_exhausted")) {
            return prefix + "The API quota or rate limit was reached. Wait briefly or check the provider quota, then retry.";
        }
        if (details.contains("401") || details.contains("403") || details.contains("api key")
                || details.contains("permission_denied") || details.contains("unauthenticated")) {
            return prefix + "The API key was rejected or lacks permission. Check the private .env key, then restart the services.";
        }
        if (details.contains("404") || details.contains("not found") || details.contains("no longer available")
                || details.contains("unsupported model")) {
            String modelAction = selection.provider().equals("Gemini")
                    ? "Update GEMINI_MODEL to gemini-3.6-flash and restart the services."
                    : "Update the configured provider model and restart the services.";
            return prefix + "The configured model '" + selection.modelName() + "' is unavailable. " + modelAction;
        }
        if (details.contains("timeout") || details.contains("timed out") || details.contains("connection")
                || details.contains("unknown host")) {
            return prefix + "The provider could not be reached. Check the internet connection and retry.";
        }
        return prefix + "The provider request failed. Check the API key, model and provider quota, then retry.";
    }

    private ExtractionResult ai(ConfiguredChatModel.Selection selection, String text) throws Exception {
        if (text.isBlank()) {
            throw new IllegalArgumentException("The document extractor returned no text");
        }
        String raw = selection.model().chat(EXTRACTION_PROMPT + "\n\nEXTRACTED DOCUMENT TEXT:\n"
                + text.substring(0, Math.min(text.length(), MAX_TEXT_LENGTH)));
        raw = raw.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        return json.readValue(raw, ExtractionResult.class);
    }

    private ExtractionResult deterministic(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        Matcher codeMatcher = SUBJECT_CODE.matcher(upper);
        String subjectCode = codeMatcher.find() ? codeMatcher.group(1).replace(" ", "") : null;
        String subjectName = findSubjectName(text, subjectCode);
        Integer creditPoints = findInteger(CREDIT_POINTS, text);
        List<AssessmentCandidate> items = findAssessments(text);
        List<String> warnings = new ArrayList<>();
        if (subjectCode == null) {
            warnings.add("Subject code was not confidently detected");
        }
        if (subjectName == null) {
            warnings.add("Subject name was not confidently detected");
        }
        if (creditPoints == null) {
            warnings.add("Credit points were not confidently detected");
        }
        if (items.isEmpty()) {
            warnings.add("No assessment rows were confidently detected; add them during review");
        }
        return new ExtractionResult(subjectCode, subjectName, creditPoints, items, warnings);
    }

    private String findSubjectName(String text, String subjectCode) {
        Matcher labelled = LABELLED_NAME.matcher(text);
        if (labelled.find()) {
            return cleanTitle(labelled.group(1));
        }
        if (subjectCode == null) {
            return null;
        }
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int codeAt = line.toUpperCase(Locale.ROOT).indexOf(subjectCode);
            if (codeAt < 0) {
                continue;
            }
            String afterCode = line.substring(codeAt + subjectCode.length())
                    .replaceFirst("^[\\s:|\\-–—]+", "").trim();
            if (looksLikeName(afterCode)) {
                return cleanTitle(afterCode);
            }
            if (i + 1 < lines.size() && looksLikeName(lines.get(i + 1))) {
                return cleanTitle(lines.get(i + 1));
            }
        }
        return null;
    }

    private boolean looksLikeName(String value) {
        return value != null && value.length() >= 4 && value.length() <= 160
                && value.matches(".*[A-Za-z]{3}.*")
                && !value.matches("(?i).*(spring|autumn|session|outline|handbook)\\s*20\\d{2}.*");
    }

    private List<AssessmentCandidate> findAssessments(String text) {
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        List<AssessmentCandidate> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size() && items.size() < 20; i++) {
            String line = lines.get(i);
            Matcher weightMatcher = WEIGHT.matcher(line);
            if (!weightMatcher.find()) {
                continue;
            }
            double weighting = Double.parseDouble(weightMatcher.group(1));
            if (weighting > 100 || line.matches("(?i).*total\\s+100\\s*%.*")) {
                continue;
            }
            String title = cleanAssessmentTitle(line.substring(0, weightMatcher.start()));
            boolean titleCameFromPreviousLine = false;
            if (!isUsefulAssessmentTitle(title) && i > 0) {
                title = cleanAssessmentTitle(lines.get(i - 1));
                titleCameFromPreviousLine = true;
            }
            if (!isUsefulAssessmentTitle(title)) {
                continue;
            }
            String key = title.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            List<String> contextLines = new ArrayList<>();
            if (titleCameFromPreviousLine) {
                contextLines.add(lines.get(i - 1));
            }
            contextLines.add(line);
            for (int nextIndex = i + 1; nextIndex < Math.min(lines.size(), i + 3); nextIndex++) {
                String nextLine = lines.get(nextIndex);
                if (WEIGHT.matcher(nextLine).find()) {
                    break;
                }
                contextLines.add(nextLine);
            }
            String context = String.join(" ", contextLines);
            LocalDate dueDate = findDate(context);
            Integer dueWeek = findInteger(DUE_WEEK, context);
            String warning = dueDate == null && dueWeek == null ? "Due date or week was not confidently detected" : null;
            items.add(new AssessmentCandidate(
                    title,
                    inferType(title),
                    weighting,
                    dueDate,
                    dueWeek,
                    null,
                    null,
                    dueDate == null && dueWeek == null ? 0.55 : 0.7,
                    warning));
        }
        return items;
    }

    private LocalDate findDate(String context) {
        for (Pattern pattern : List.of(ISO_DATE, SLASH_DATE, NAMED_DATE)) {
            Matcher matcher = pattern.matcher(context);
            while (matcher.find()) {
                String candidate = matcher.group(1).replaceAll("(?i)(\\d)(st|nd|rd|th)", "$1");
                for (DateTimeFormatter formatter : DATE_FORMATS) {
                    try {
                        return LocalDate.parse(candidate, formatter);
                    } catch (DateTimeParseException ignored) {
                        // Try the next supported subject-outline date format.
                    }
                }
            }
        }
        return null;
    }

    private Integer findInteger(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String cleanAssessmentTitle(String value) {
        if (value == null) {
            return null;
        }
        return cleanTitle(value
                .replaceFirst("^[•*\\-–—|\\s]+", "")
                .replaceFirst("(?i)^assessment\\s*(?:task)?\\s*\\d*\\s*[:.\\-–—]?\\s*", "")
                .replaceFirst("(?i)^(?:task|item)\\s*\\d+\\s*[:.\\-–—]?\\s*", ""));
    }

    private String cleanTitle(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").replaceAll("[|:;\\-–—\\s]+$", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isUsefulAssessmentTitle(String title) {
        return title != null && title.length() >= 3 && title.length() <= 120
                && !NON_ASSESSMENT_TITLE.matcher(title).matches()
                && title.chars().filter(Character::isLetter).count() >= 3;
    }

    private String inferType(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        for (String type : List.of("Exam", "Quiz", "Assignment", "Report", "Presentation", "Project", "Test", "Portfolio", "Laboratory")) {
            if (lower.contains(type.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return "Assessment";
    }

    private ExtractionResult validate(ExtractionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("AI extraction returned no result");
        }
        List<AssessmentCandidate> supplied = result.assessments() == null ? List.of() : result.assessments();
        List<AssessmentCandidate> assessments = new ArrayList<>();
        List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
        Set<String> seen = new HashSet<>();
        int removed = 0;
        for (AssessmentCandidate assessment : supplied) {
            if (assessment.title() == null || assessment.title().isBlank()) {
                removed++;
                continue;
            }
            if (assessment.weighting() != null && (assessment.weighting() < 0 || assessment.weighting() > 100)) {
                throw new IllegalArgumentException("Assessment weighting is invalid");
            }
            if (!isUsefulAssessmentTitle(assessment.title())) {
                removed++;
                continue;
            }
            if (!seen.add(assessment.title().trim().toLowerCase(Locale.ROOT))) {
                removed++;
                continue;
            }
            assessments.add(assessment);
        }
        if (removed > 0) {
            warnings.add(removed + " low-confidence or policy-like row(s) were removed before review");
        }
        return new ExtractionResult(
                blankToNull(result.subjectCode()),
                blankToNull(result.subjectName()),
                result.creditPoints(),
                assessments,
                warnings);
    }

    private ExtractionResult withWarning(ExtractionResult result, String warning) {
        List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
        warnings.add(warning);
        return new ExtractionResult(
                result.subjectCode(), result.subjectName(), result.creditPoints(), result.assessments(), warnings);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
