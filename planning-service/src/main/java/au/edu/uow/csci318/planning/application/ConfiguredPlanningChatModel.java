package au.edu.uow.csci318.planning.application;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
class ConfiguredPlanningChatModel {
    record Selection(String provider, ChatModel model) {
    }

    private final String provider;
    private final String geminiKey;
    private final String geminiModel;
    private final String openAiKey;
    private final String openAiModel;

    ConfiguredPlanningChatModel(
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
            return Optional.of(new Selection(
                    "Gemini",
                    GoogleAiGeminiChatModel.builder()
                            .apiKey(geminiKey)
                            .modelName(geminiModel)
                            .temperature(0.0)
                            .responseFormat(ResponseFormat.JSON)
                            .build()));
        }
        if ((provider.equals("auto") || provider.equals("openai")) && !openAiKey.isBlank()) {
            return Optional.of(new Selection(
                    "OpenAI",
                    OpenAiChatModel.builder()
                            .apiKey(openAiKey)
                            .modelName(openAiModel)
                            .temperature(0.0)
                            .build()));
        }
        return Optional.empty();
    }
}
