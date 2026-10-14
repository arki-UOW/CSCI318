package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AiStatus;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredPlanningChatModel {
  record Selection(String provider, String modelName, ChatModel model) {}

  private final String provider;
  private final String geminiKey;
  private final String geminiModel;
  private final String openAiKey;
  private final String openAiModel;

  @Value("${study.ai.openai.base-url:https://api.openai.com/v1}")
  private String openAiBaseUrl = "https://api.openai.com/v1";

  ConfiguredPlanningChatModel(
      @Value("${study.ai.provider:auto}") String provider,
      @Value("${study.ai.gemini.api-key:}") String geminiKey,
      @Value("${study.ai.gemini.model:gemini-3.6-flash}") String geminiModel,
      @Value("${study.ai.openai.api-key:}") String openAiKey,
      @Value("${study.ai.openai.model:gpt-4.1-mini}") String openAiModel) {
    this.provider = provider == null ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
    this.geminiKey = geminiKey == null ? "" : geminiKey.trim();
    this.geminiModel = geminiModel == null ? "gemini-3.6-flash" : geminiModel.trim();
    this.openAiKey = openAiKey == null ? "" : openAiKey.trim();
    this.openAiModel = openAiModel == null ? "gpt-4.1-mini" : openAiModel.trim();
  }

  Optional<Selection> selection() {
    if (!Set.of("auto", "gemini", "openai").contains(provider)) {
      throw new IllegalArgumentException("AI_PROVIDER must be auto, gemini, or openai");
    }
    if ((provider.equals("auto") || provider.equals("gemini")) && !geminiKey.isBlank()) {
      return Optional.of(
          new Selection(
              "Gemini",
              geminiModel,
              GoogleAiGeminiChatModel.builder()
                  .timeout(java.time.Duration.ofSeconds(60))
                  .maxRetries(1)
                  .apiKey(geminiKey)
                  .modelName(geminiModel)
                  .build()));
    }
    if ((provider.equals("auto") || provider.equals("openai")) && !openAiKey.isBlank()) {
      return Optional.of(
          new Selection(
              "OpenAI",
              openAiModel,
              OpenAiChatModel.builder()
                  .baseUrl(openAiBaseUrl)
                  .timeout(java.time.Duration.ofSeconds(60))
                  .maxRetries(1)
                  .apiKey(openAiKey)
                  .modelName(openAiModel)
                  .temperature(0.0)
                  .build()));
    }
    return Optional.empty();
  }

  AiStatus status() {
    Optional<Selection> selected = selection();
    if (selected.isPresent()) {
      return new AiStatus(
          selected.get().provider(),
          selected.get().modelName(),
          true,
          selected.get().provider() + " is configured in the running Planning Service");
    }
    String label = provider.equals("openai") ? "OpenAI" : "Gemini";
    String model = provider.equals("openai") ? openAiModel : geminiModel;
    return new AiStatus(
        label,
        model,
        false,
        label
            + " is selected but its API key is not available inside the running Planning Service");
  }

  static String failureMessage(Selection selection, Exception failure, String action) {
    StringBuilder messages = new StringBuilder();
    Integer upstreamStatus = null;
    Throwable current = failure;
    while (current != null) {
      if (current instanceof dev.langchain4j.exception.HttpException http) {
        upstreamStatus = http.statusCode();
      }
      if (current.getMessage() != null) {
        messages.append(' ').append(current.getMessage().toLowerCase(Locale.ROOT));
      }
      current = current.getCause();
    }
    String details = messages.toString();
    // Only expose the status and our own guidance, never the raw provider body or URL.
    String upstream = upstreamStatus == null ? "" : "Upstream HTTP " + upstreamStatus + ". ";
    String prefix = selection.provider() + " could not " + action + ". ";
    prefix += upstream;
    if (Integer.valueOf(429).equals(upstreamStatus) || details.contains("429")
        || details.contains("quota")
        || details.contains("resource_exhausted")) {
      return prefix + "The API quota or rate limit was reached. Wait briefly and retry.";
    }
    if (Integer.valueOf(401).equals(upstreamStatus)
        || Integer.valueOf(403).equals(upstreamStatus)
        || details.contains("401")
        || details.contains("403")
        || details.contains("api key")
        || details.contains("permission_denied")
        || details.contains("unauthenticated")) {
      return prefix
          + "The API key was rejected or lacks permission. Check .env and rebuild the containers.";
    }
    if (Integer.valueOf(404).equals(upstreamStatus) || details.contains("404")
        || details.contains("not found")
        || details.contains("no longer available")) {
      return prefix + "The configured model '" + selection.modelName() + "' is unavailable.";
    }
    if ((upstreamStatus != null && upstreamStatus >= 500)
        || details.contains("503") || details.contains("unavailable")
        || details.contains("overloaded") || details.contains("high demand")) {
      return prefix
          + "The provider is temporarily unavailable or overloaded. Retry after a short wait."
          + " This error does not establish that the API key is invalid.";
    }
    if (Integer.valueOf(400).equals(upstreamStatus)
        || details.contains("invalid_argument") || details.contains("bad request")) {
      return prefix
          + "The provider rejected the request format or model parameters. Run the Gemini"
          + " diagnostic and check the configured model; repeatedly retrying will not fix this.";
    }
    if (details.contains("timeout")
        || details.contains("connection")
        || details.contains("unknown host")) {
      return prefix + "The provider could not be reached. Check the internet connection and retry.";
    }
    return prefix + "The provider response was invalid. Retry or check the model configuration.";
  }
}
