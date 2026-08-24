package au.edu.uow.csci318.subject.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlineExtractionTest {
    @Test
    void deterministicFallbackExtractsLabelledSubjectAndAssessmentRows() {
        ConfiguredChatModel model = new ConfiguredChatModel(
                "auto", "", "gemini-2.5-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);
        String text = """
                Subject Code: CSCI318
                Subject Name: Software Engineering Practices and Principles
                Credit Points: 6

                Assessment 1: Architecture Report 25% Due: 14/09/2026
                Assessment 2: Demo Presentation 30% Due Week 10
                """;

        var result = extractor.extract(new byte[]{1}, text);

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
                "auto", "", "gemini-2.5-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);

        var result = extractor.extract(new byte[]{1}, """
                CSCI318 Software Engineering Practices and Principles
                Assessment: Group Project 40% Due Week 8
                """);

        assertEquals(8, result.assessments().getFirst().dueWeek());
        assertNull(result.assessments().getFirst().dueDate());
    }

    @Test
    void fallbackDoesNotBorrowTheNextAssessmentDate() {
        ConfiguredChatModel model = new ConfiguredChatModel(
                "auto", "", "gemini-2.5-flash", "", "gpt-4.1-mini");
        SafeOutlineExtractor extractor = new SafeOutlineExtractor(new ObjectMapper(), model);

        var result = extractor.extract(new byte[]{1}, """
                CSCI318 Software Engineering Practices and Principles
                Assessment 1: Architecture Report 25%
                Assessment 2: Demo Presentation 30% Due: 25/10/2026
                """);

        assertNull(result.assessments().getFirst().dueDate());
        assertEquals(LocalDate.of(2026, 10, 25), result.assessments().get(1).dueDate());
    }
}
