package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import au.edu.uow.csci318.planning.dto.PlanningDtos.WorkloadSummary;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgenticPlanningAdvisorTest {
  @Test
  void modelUsesRequiredApplicationToolsBeforeSubmittingDecision() {
    ConfiguredPlanningChatModel configured = mock(ConfiguredPlanningChatModel.class);
    ChatModel model = mock(ChatModel.class);
    PlanningTools tools = mock(PlanningTools.class);
    DashboardQueryService dashboard = mock(DashboardQueryService.class);
    StudyPlanRepository plans = mock(StudyPlanRepository.class);
    when(configured.selection())
        .thenReturn(
            Optional.of(new ConfiguredPlanningChatModel.Selection("Gemini", "test-model", model)));
    when(tools.getIncompleteAssessments("Bearer test")).thenReturn(List.of());
    when(dashboard.workload(any(), any())).thenReturn(new WorkloadSummary(0, 0, 0, 0, 0, "LOW"));
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(
            response(
                call("1", "getIncompleteAssessments", "{}"),
                call("2", "getCurrentWorkload", "{}"),
                call("progress", "getStudyProgress", "{}")),
            response(
                call(
                    "3",
                    "saveStudyPlan",
                    "{\"action\":\"GENERATE\",\"items\":[],\"summary\":\"No conflicting workload"
                        + " was found.\"}")));

    AgenticPlanningAdvisor advisor =
        new AgenticPlanningAdvisor(configured, tools, dashboard, plans, new ObjectMapper());
    LocalDate monday = LocalDate.of(2026, 10, 5);
    var availability = new LinkedHashMap<LocalDate, Integer>();
    for (int day = 0; day < 7; day++) availability.put(monday.plusDays(day), 60);

    AgenticPlanningAdvisor.Advice advice =
        advisor.adviseGenerate(
            UUID.randomUUID(),
            "Bearer test",
            ZoneId.of("Australia/Sydney"),
            new PlanRequest(monday, availability),
            new StudyPlanningAgent.Schedule(List.of(), monday.plusDays(6), 0, 0));

    assertEquals("Gemini", advice.provider());
    assertTrue(advice.toolsUsed().contains("getIncompleteAssessments"));
    assertTrue(advice.toolsUsed().contains("getCurrentWorkload"));
    assertTrue(advice.toolsUsed().contains("saveStudyPlan"));
  }

  @Test
  void regenerationReadsExistingPlanBeforeSubmittingDecision() {
    ConfiguredPlanningChatModel configured = mock(ConfiguredPlanningChatModel.class);
    ChatModel model = mock(ChatModel.class);
    PlanningTools tools = mock(PlanningTools.class);
    DashboardQueryService dashboard = mock(DashboardQueryService.class);
    StudyPlanRepository plans = mock(StudyPlanRepository.class);
    UUID owner = UUID.randomUUID();
    UUID existingId = UUID.randomUUID();
    LocalDate monday = LocalDate.of(2026, 10, 5);
    when(configured.selection())
        .thenReturn(
            Optional.of(new ConfiguredPlanningChatModel.Selection("Gemini", "test-model", model)));
    when(tools.getIncompleteAssessments("Bearer test")).thenReturn(List.of());
    when(dashboard.workload(any(), any())).thenReturn(new WorkloadSummary(0, 0, 0, 0, 0, "LOW"));
    when(plans.findByIdAndOwnerId(existingId, owner))
        .thenReturn(
            Optional.of(new StudyPlan(owner, monday, monday.plusDays(6), 1, "[]", "Original")));
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(
            response(
                call("1", "getIncompleteAssessments", "{}"),
                call("2", "getCurrentWorkload", "{}"),
                call("progress", "getStudyProgress", "{}"),
                call("3", "getExistingStudyPlan", "{}")),
            response(
                call(
                    "4",
                    "saveStudyPlan",
                    "{\"action\":\"REGENERATE\",\"items\":[],\"summary\":\"Current state was"
                        + " re-read.\"}")));
    AgenticPlanningAdvisor advisor =
        new AgenticPlanningAdvisor(configured, tools, dashboard, plans, new ObjectMapper());
    var availability = new LinkedHashMap<LocalDate, Integer>();
    availability.put(monday, 60);

    AgenticPlanningAdvisor.Advice advice =
        advisor.adviseRegenerate(
            owner,
            "Bearer test",
            ZoneId.of("Australia/Sydney"),
            new PlanRequest(monday, availability),
            existingId,
            new StudyPlanningAgent.Schedule(List.of(), monday.plusDays(6), 0, 0));

    assertTrue(advice.toolsUsed().contains("getExistingStudyPlan"));
    assertEquals("Current state was re-read.", advice.summary());
  }

  @Test
  void rejectsSubmissionThatSkippedRequiredTools() {
    ConfiguredPlanningChatModel configured = mock(ConfiguredPlanningChatModel.class);
    ChatModel model = mock(ChatModel.class);
    when(configured.selection())
        .thenReturn(
            Optional.of(new ConfiguredPlanningChatModel.Selection("Gemini", "test-model", model)));
    when(model.chat(any(ChatRequest.class)))
        .thenReturn(
            response(
                call(
                    "1",
                    "saveStudyPlan",
                    "{\"action\":\"GENERATE\",\"items\":[],\"summary\":\"Skip the checks.\"}")));
    AgenticPlanningAdvisor advisor =
        new AgenticPlanningAdvisor(
            configured,
            mock(PlanningTools.class),
            mock(DashboardQueryService.class),
            mock(StudyPlanRepository.class),
            new ObjectMapper());
    LocalDate monday = LocalDate.of(2026, 10, 5);
    var availability = new LinkedHashMap<LocalDate, Integer>();
    availability.put(monday, 60);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                advisor.adviseGenerate(
                    UUID.randomUUID(),
                    "Bearer test",
                    ZoneId.of("Australia/Sydney"),
                    new PlanRequest(monday, availability),
                    new StudyPlanningAgent.Schedule(List.of(), monday.plusDays(6), 0, 0)));

    assertTrue(failure.getMessage().contains("did not submit a decision"));
  }

  private ChatResponse response(ToolExecutionRequest... calls) {
    return ChatResponse.builder().aiMessage(AiMessage.from(List.of(calls))).build();
  }

  private ToolExecutionRequest call(String id, String name, String arguments) {
    return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
  }
}
