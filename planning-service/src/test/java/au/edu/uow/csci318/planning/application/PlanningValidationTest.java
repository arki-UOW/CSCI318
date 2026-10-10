package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PlanningValidationTest {
  private static final UUID OWNER = UUID.randomUUID(),
      SUBJECT = UUID.randomUUID(),
      ASSESSMENT = UUID.randomUUID();
  private static final LocalDate START = LocalDate.of(2026, 10, 12);
  private final StudyPlanRepository plans = mock(StudyPlanRepository.class);
  private final StudyPlanningAgent scheduler = mock(StudyPlanningAgent.class);
  private final AgenticPlanningAdvisor advisor = mock(AgenticPlanningAdvisor.class);
  private final PlanningTools tools = mock(PlanningTools.class);
  private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
  private final PlanningApplicationService service =
      new PlanningApplicationService(
          plans,
          scheduler,
          advisor,
          tools,
          mock(AvailabilityAssistant.class),
          mock(ConfiguredPlanningChatModel.class),
          calendar,
          new ObjectMapper());

  static Stream<PlanItem> invalidItems() {
    return Stream.of(
        new PlanItem(START, SUBJECT, UUID.randomUUID(), "Missing or completed", 30),
        new PlanItem(START, UUID.randomUUID(), ASSESSMENT, "Wrong subject", 30),
        new PlanItem(START.minusDays(1), SUBJECT, ASSESSMENT, "Before period", 30),
        new PlanItem(START.plusDays(8), SUBJECT, ASSESSMENT, "After period", 30),
        new PlanItem(START.plusDays(3), SUBJECT, ASSESSMENT, "After deadline", 30),
        new PlanItem(START, SUBJECT, ASSESSMENT, "Zero duration", 0),
        new PlanItem(START, SUBJECT, ASSESSMENT, "Over daily capacity", 61),
        new PlanItem(null, SUBJECT, ASSESSMENT, "Null date", 30),
        new PlanItem(START, SUBJECT, ASSESSMENT, " ", 30),
        new PlanItem(START, SUBJECT, ASSESSMENT, "Overflow", Integer.MAX_VALUE),
        new PlanItem(START, SUBJECT, ASSESSMENT, "Invalid stage", 30, -1));
  }

  @ParameterizedTest
  @MethodSource("invalidItems")
  void invalidCandidateNeverReachesPersistence(PlanItem item) {
    when(advisor.adviseGenerate(any(), any(), any(), any(), any()))
        .thenReturn(
            new AgenticPlanningAdvisor.Advice("test", "test", "test", List.of(), List.of(item)));
    when(scheduler.generate(any(), any(), any()))
        .thenReturn(new StudyPlanningAgent.Schedule(List.of(item), START.plusDays(6), 60, 60));
    when(tools.getIncompleteAssessments(any()))
        .thenReturn(
            List.of(
                new AssessmentView(
                    ASSESSMENT,
                    SUBJECT,
                    "AI",
                    "Report",
                    20.0,
                    START.plusDays(2),
                    null,
                    null,
                    60,
                    "HIGH",
                    "INCOMPLETE",
                    Instant.EPOCH)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.generate(
                OWNER,
                "Bearer test",
                ZoneId.of("UTC"),
                new PlanRequest(START, Map.of(START, 60, START.plusDays(3), 60))));
    verifyNoInteractions(plans, calendar);
  }

  @Test
  void allocationCannotExceedRemainingWorkEvenWhenDailyCapacityAllowsIt() {
    var item = new PlanItem(START, SUBJECT, ASSESSMENT, "Remaining work", 45);
    when(scheduler.generate(any(), any(), any()))
        .thenReturn(new StudyPlanningAgent.Schedule(List.of(), START.plusDays(6), 30, 30));
    when(scheduler.completedMinutes(OWNER)).thenReturn(Map.of(ASSESSMENT, 30));
    when(advisor.adviseGenerate(any(), any(), any(), any(), any()))
        .thenReturn(
            new AgenticPlanningAdvisor.Advice("test", "test", "test", List.of(), List.of(item)));
    when(tools.getIncompleteAssessments(any()))
        .thenReturn(
            List.of(
                new AssessmentView(
                    ASSESSMENT,
                    SUBJECT,
                    "Report",
                    "Report",
                    30.0,
                    START.plusDays(5),
                    null,
                    null,
                    60,
                    "HIGH",
                    "INCOMPLETE",
                    Instant.EPOCH)));
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.generate(
                    OWNER,
                    "Bearer test",
                    ZoneId.of("UTC"),
                    new PlanRequest(START, Map.of(START, 120))));
    assertEquals("Plan exceeds remaining assessment workload", failure.getMessage());
    verifyNoInteractions(plans, calendar);
  }

  @Test
  void inconsistentSlotCapacityIsRejectedBeforeUsingTheProvider() {
    var request =
        new PlanRequest(
            START,
            Map.of(START, 120),
            Map.of(START, List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.generate(OWNER, "Bearer test", ZoneId.of("UTC"), request));
    verifyNoInteractions(advisor, scheduler, plans, calendar);
  }

  @Test
  void providerFailureCannotWriteAPlanOrChangeCalendar() {
    when(advisor.adviseGenerate(any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("Provider unavailable"));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.generate(
                OWNER, "Bearer test", ZoneId.of("UTC"), new PlanRequest(START, Map.of(START, 60))));
    verifyNoInteractions(plans, calendar);
  }
}
