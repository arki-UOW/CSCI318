package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import au.edu.uow.csci318.planning.controller.PlanningController;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import au.edu.uow.csci318.planning.exception.ApiExceptionHandler;
import au.edu.uow.csci318.planning.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProviderFailureApiTest {
  @ParameterizedTest
  @CsvSource({
    "429 quota,quota or rate limit",
    "401 api key,key was rejected",
    "404 not found,unavailable",
    "connection timeout,could not be reached"
  })
  void sdkFailuresBecomeSafeActionable503Responses(String detail, String expected)
      throws Exception {
    var configured = mock(ConfiguredPlanningChatModel.class);
    var model = mock(ChatModel.class);
    when(configured.selection())
        .thenReturn(
            Optional.of(new ConfiguredPlanningChatModel.Selection("OpenAI", "test", model)));
    when(model.chat(any(ChatRequest.class)))
        .thenThrow(new dev.langchain4j.exception.RateLimitException(detail + " PRIVATE_SENTINEL"));
    var plans = mock(StudyPlanRepository.class);
    var advisor =
        new AgenticPlanningAdvisor(
            configured,
            mock(PlanningTools.class),
            mock(DashboardQueryService.class),
            plans,
            new ObjectMapper());
    UUID owner = UUID.randomUUID();
    var identity = mock(IdentityClient.class);
    when(identity.require("Bearer test")).thenReturn(owner);
    var service = mock(PlanningApplicationService.class);
    when(service.generate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              PlanRequest request = invocation.getArgument(3);
              return advisor.adviseGenerate(
                  owner,
                  "Bearer test",
                  ZoneId.of("UTC"),
                  request,
                  new StudyPlanningAgent.Schedule(
                      List.of(), request.startDate().plusDays(6), 0, 0));
            });
    var api =
        MockMvcBuilders.standaloneSetup(new PlanningController(service, identity))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    String body =
        api.perform(
                post("/api/planning/plans")
                    .header("Authorization", "Bearer test")
                    .contentType("application/json")
                    .content(
                        "{\"startDate\":\"2026-10-12\",\"dailyAvailabilityMinutes\":{\"2026-10-12\":60}}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.path").value("/api/planning/plans"))
            .andExpect(jsonPath("$.validationErrors").isMap())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(body.contains(expected), body);
    assertFalse(body.contains("PRIVATE_SENTINEL"));
    verifyNoInteractions(plans);
  }
}
