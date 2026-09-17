package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.application.SubjectAiConfiguration;
import au.edu.uow.csci318.subject.dto.SubjectDtos.AiStatus;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredChatModel implements SubjectAiConfiguration {
  record Selection(String provider, String modelName, ChatModel model) {}

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
    this.geminiModel =
        geminiModel == null || geminiModel.isBlank() ? "gemini-3.6-flash" : geminiModel.trim();
    this.openAiKey = openAiKey == null ? "" : openAiKey.trim();
    this.openAiModel =
        openAiModel == null || openAiModel.isBlank() ? "gpt-4.1-mini" : openAiModel.trim();
  }

  Optional<Selection> selection() {
    if (!Set.of("auto", "gemini", "openai").contains(provider)) {
      throw new IllegalArgumentException("AI_PROVIDER must be auto, gemini, or openai");
    }
    if ((provider.equals("auto") || provider.equals("gemini")) && !geminiKey.isBlank()) {
      ChatModel model =
          GoogleAiGeminiChatModel.builder()
              .apiKey(geminiKey)
              .modelName(geminiModel)
              .temperature(0.0)
              .responseFormat(ResponseFormat.JSON)
              .maxOutputTokens(8192)
              .timeout(Duration.ofSeconds(120))
              .maxRetries(2)
              .build();
      return Optional.of(new Selection("Gemini", geminiModel, model));
    }
    if ((provider.equals("auto") || provider.equals("openai")) && !openAiKey.isBlank()) {
      ChatModel model =
          OpenAiChatModel.builder()
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

  public AiStatus status() {
    Optional<Selection> selected = selection();
    if (selected.isPresent()) {
      return new AiStatus(
          selected.get().provider(),
          selected.get().modelName(),
          true,
          selected.get().provider() + " is configured in the running Subject Service");
    }
    String model = provider.equals("openai") ? openAiModel : geminiModel;
    String label = provider.equals("openai") ? "OpenAI" : "Gemini";
    return new AiStatus(label, model, false, missingConfigurationMessage());
  }
}
