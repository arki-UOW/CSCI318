package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudyPlanningAgentTest {
  @Test
  void plansEstimatedMinutesAcrossSpacedSessionsUntilTheDueDate() {
    PlanningTools tools = mock(PlanningTools.class);
    StudyPlanningAgent agent = new StudyPlanningAgent(tools, mock(StudyHistory.class));
    LocalDate start = LocalDate.of(2026, 9, 7);
    LocalDate due = start.plusDays(60);
    AssessmentView assessment =
        new AssessmentView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Architecture report",
            "Report",
            30.0,
            due,
            null,
            null,
            180,
            "HIGH",
            "INCOMPLETE",
            Instant.now());
    when(tools.getIncompleteAssessments("Bearer test")).thenReturn(List.of(assessment));

    StudyPlanningAgent.Schedule result =
        agent.generate(
            UUID.randomUUID(), new PlanRequest(start, availableWeek(start, 60)), "Bearer test");

    assertEquals(due, result.endDate());
    assertEquals(180, result.requestedMinutes());
    assertEquals(180, result.scheduledMinutes());
    assertEquals(0, result.unscheduledMinutes());
    assertTrue(result.items().size() >= 5);
    assertTrue(result.items().stream().anyMatch(item -> item.repetitionStage() > 0));
    assertTrue(
        result.items().stream()
            .map(item -> item.date())
            .max(LocalDate::compareTo)
            .orElseThrow()
            .isAfter(start.plusDays(30)));
    assertTrue(result.items().stream().allMatch(item -> item.date().isBefore(due)));
  }

  @Test
  void requiresAtLeastOneAvailableStudyPeriod() {
    PlanningTools tools = mock(PlanningTools.class);
    StudyPlanningAgent agent = new StudyPlanningAgent(tools, mock(StudyHistory.class));
    LocalDate start = LocalDate.of(2026, 9, 7);
    AssessmentView assessment =
        new AssessmentView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Exam",
            "Exam",
            50.0,
            start.plusDays(14),
            null,
            null,
            120,
            "HIGH",
            "INCOMPLETE",
            Instant.now());
    when(tools.getIncompleteAssessments("Bearer test")).thenReturn(List.of(assessment));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            agent.generate(
                UUID.randomUUID(), new PlanRequest(start, availableWeek(start, 0)), "Bearer test"));
  }

  private Map<LocalDate, Integer> availableWeek(LocalDate start, int minutes) {
    Map<LocalDate, Integer> result = new LinkedHashMap<>();
    for (int day = 0; day < 7; day++) result.put(start.plusDays(day), minutes);
    return result;
  }
}
