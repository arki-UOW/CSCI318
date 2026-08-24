package au.edu.uow.csci318.subject.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlineExtractionTest {
    @Test
    void deterministicFallbackExtractsLabelledSubjectAndAssessmentRows() {
        ConfiguredChatModel model = new ConfiguredChatModel(
                "auto", "", "gemini-3.6-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);
        String text = """
                Subject Code: CSCI318
                Subject Name: Software Engineering Practices and Principles
                Credit Points: 6

                Assessment 1: Architecture Report 25% Due: 14/09/2026
                Assessment 2: Demo Presentation 30% Due Week 10
                """;

        var result = extractor.extract(text);

        assertEquals("CSCI318", result.subjectCode());
        assertEquals("Software Engineering Practices and Principles", result.subjectName());
        assertEquals(6, result.creditPoints());
        assertEquals(2, result.assessments().size());
        assertEquals("Architecture Report", result.assessments().getFirst().title());
        assertEquals(LocalDate.of(2026, 9, 14), result.assessments().getFirst().dueDate());
        assertEquals(10, result.assessments().get(1).dueWeek());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("No AI API key")));
    }

    @Test
    void fallbackDoesNotInventAnExactDateFromAWeekNumber() {
        ConfiguredChatModel model = new ConfiguredChatModel(
                "auto", "", "gemini-3.6-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);

        var result = extractor.extract("""
                CSCI318 Software Engineering Practices and Principles
                Assessment: Group Project 40% Due Week 8
                """);

        assertEquals(8, result.assessments().getFirst().dueWeek());
        assertNull(result.assessments().getFirst().dueDate());
    }

    @Test
    void fallbackDoesNotBorrowTheNextAssessmentDate() {
        ConfiguredChatModel model = new ConfiguredChatModel(
                "auto", "", "gemini-3.6-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);

        var result = extractor.extract("""
                CSCI318 Software Engineering Practices and Principles
                Assessment 1: Architecture Report 25%
                Assessment 2: Demo Presentation 30% Due: 25/10/2026
                """);

        assertNull(result.assessments().getFirst().dueDate());
        assertEquals(LocalDate.of(2026, 10, 25), result.assessments().get(1).dueDate());
    }

    @Test
    void configuredProviderFailureIsActionableAndDoesNotReturnDeterministicGarbage() {
        ChatModel failingModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatMessage... messages) {
                throw new RuntimeException("404: this model is no longer available");
            }
        };
        ConfiguredChatModel configured = new ConfiguredChatModel(
                "gemini", "configured-key", "retired-model", "", "gpt-4.1-mini") {
            @Override
            Optional<Selection> selection() {
                return Optional.of(new Selection("Gemini", "retired-model", failingModel));
            }
        };
        String error = SafeOutlineExtractor.providerFailure(configured.selection().orElseThrow(),
                new RuntimeException("404: this model is no longer available"));

        assertTrue(error.contains("retired-model"));
        assertTrue(error.contains("gemini-3.6-flash"));
    }
}
