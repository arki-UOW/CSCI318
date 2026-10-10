package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.domain.*;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.*;
import au.edu.uow.csci318.planning.infrastructure.projection.ProjectionDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@ContextConfiguration(classes = PlanningPersistenceIntegrationTest.Config.class)
class PlanningPersistenceIntegrationTest {
  @Configuration
  @EntityScan(basePackageClasses = {StudyPlan.class, ProjectionDocument.class})
  @EnableJpaRepositories(basePackageClasses = StudyPlanRepository.class)
  @Import({
    PlanningApplicationService.class,
    StudyPlanningAgent.class,
    AgenticPlanningAdvisor.class,
    CalendarApplicationService.class
  })
  static class Config {
    @Bean
    ObjectMapper json() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }

  @MockitoBean CalendarReferences references;
  @MockitoBean PlanningTools tools;
  @MockitoBean DashboardQueryService dashboard;
  @MockitoBean ConfiguredPlanningChatModel configured;
  @MockitoBean AvailabilityAssistant availability;
  @MockitoBean CompletedStudyBlockEvents events;
  @Autowired PlanningApplicationService service;
  @Autowired StudyPlanRepository plans;
  @Autowired CalendarEntryRepository calendar;
  @Autowired EntityManager entityManager;

  @Test
  void realAgentLoopGeneratesAndRegeneratesPersistedPlansAfterAssessmentChanges() {
    UUID owner = UUID.randomUUID(), subject = UUID.randomUUID(), assessment = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 10, 12);
    ChatModel model = mock(ChatModel.class);
    when(configured.selection())
        .thenReturn(
            Optional.of(new ConfiguredPlanningChatModel.Selection("Gemini", "test", model)));
    when(dashboard.workload(any(), any()))
        .thenReturn(new WorkloadSummary(1, 1, 1, 120, 1, "MEDIUM"));
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(
            response(
                call("getIncompleteAssessments", "{}"),
                call("getCurrentWorkload", "{}"),
                call("getStudyProgress", "{}")),
            response(call("saveStudyPlan", decision("GENERATE", start, subject, assessment, 60))),
            response(
                call("getIncompleteAssessments", "{}"),
                call("getCurrentWorkload", "{}"),
                call("getStudyProgress", "{}"),
                call("getExistingStudyPlan", "{}")),
            response(
                call("saveStudyPlan", decision("REGENERATE", start, subject, assessment, 30))));
    when(tools.getIncompleteAssessments("Bearer test"))
        .thenReturn(List.of(assessment(assessment, subject, start.plusDays(1), 120)));
    PlanRequest request =
        new PlanRequest(
            start,
            Map.of(start, 60),
            Map.of(start, List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))));

    PlanResponse first =
        service.generate(owner, "Bearer test", ZoneId.of("Australia/Sydney"), request);
    entityManager.flush();
    entityManager.clear();
    StudyPlan saved = plans.findById(first.id()).orElseThrow();
    assertTrue(saved.getExplanation().length() > 750);
    assertTrue(saved.getExplanation().contains("beyond this plan: 60 minutes"));
    assertEquals(first.explanation(), saved.getExplanation());
    assertEquals(60, first.items().stream().mapToInt(PlanItem::allocatedMinutes).sum());
    assertEquals(LocalTime.of(9, 0), calendar.findAll().getFirst().getStartAt().toLocalTime());

    // A changed deadline/workload and completed linked work must affect the new version.
    when(tools.getIncompleteAssessments("Bearer test"))
        .thenReturn(List.of(assessment(assessment, subject, start.plusDays(3), 45)));
    when(dashboard.completedAssessmentMinutes(owner)).thenReturn(Map.of(assessment, 15));
    PlanResponse second =
        service.regenerate(
            owner, "Bearer test", ZoneId.of("Australia/Sydney"), first.id(), request);
    entityManager.flush();
    entityManager.clear();
    assertEquals(2, second.version());
    assertNotEquals(first.id(), second.id());
    assertEquals(start.plusDays(6), second.endDate());
    assertEquals(30, second.items().stream().mapToInt(PlanItem::allocatedMinutes).sum());
    assertEquals(2, plans.count());
    assertEquals("Model submitted task", second.items().getFirst().title());
    assertEquals(
        List.of(second.id(), first.id()),
        service.history(owner).stream().map(PlanResponse::id).toList());
    assertEquals(first.items(), service.get(owner, first.id()).items());
    assertEquals(first.explanation(), service.get(owner, first.id()).explanation());
    assertTrue(service.history(UUID.randomUUID()).isEmpty());
    assertThrows(NoSuchElementException.class, () -> service.get(UUID.randomUUID(), first.id()));
    assertEquals(second.explanation(), plans.findById(second.id()).orElseThrow().getExplanation());
    assertTrue(second.explanation().contains("getExistingStudyPlan"));
    assertTrue(
        calendar.findAll().stream()
            .allMatch(
                entry ->
                    entry.getPlanId().equals(second.id())
                        && !entry.getStartAt().toLocalTime().isBefore(LocalTime.of(9, 0))
                        && !entry.getEndAt().toLocalTime().isAfter(LocalTime.of(10, 0))));
    verify(model, times(4)).chat(any(ChatRequest.class));
  }

  private AssessmentView assessment(UUID id, UUID subject, LocalDate due, int minutes) {
    return new AssessmentView(
        id,
        subject,
        "AI",
        "Report",
        30.0,
        due,
        null,
        null,
        minutes,
        "HIGH",
        "INCOMPLETE",
        Instant.now());
  }

  private String decision(
      String action, LocalDate date, UUID subject, UUID assessment, int minutes) {
    try {
      return new ObjectMapper()
          .findAndRegisterModules()
          .writeValueAsString(
              Map.of(
                  "action",
                  action,
                  "summary",
                  "x".repeat(500),
                  "items",
                  List.of(
                      new PlanItem(date, subject, assessment, "Model submitted task", minutes))));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ToolExecutionRequest call(String name, String args) {
    return ToolExecutionRequest.builder()
        .id(UUID.randomUUID().toString())
        .name(name)
        .arguments(args)
        .build();
  }

  private ChatResponse response(ToolExecutionRequest... calls) {
    return ChatResponse.builder().aiMessage(AiMessage.from(List.of(calls))).build();
  }
}
