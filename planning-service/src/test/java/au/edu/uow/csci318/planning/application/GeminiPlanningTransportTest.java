package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import au.edu.uow.csci318.planning.dto.PlanningDtos.WorkloadSummary;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiPlanningTransportTest {
  private final ObjectMapper json = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void actualConfiguredSdkPreservesSignaturesAndSendsValidToolDeclarations(boolean regenerate) throws Exception {
    List<JsonNode> requests = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
      JsonNode request = json.readTree(exchange.getRequestBody());
      requests.add(request);
      String problem = null;
      for (JsonNode tool : request.path("tools")) {
        for (JsonNode declaration : tool.path("functionDeclarations")) {
          if (!declaration.path("name").asText().equals("saveStudyPlan")
              && declaration.hasNonNull("parameters")) {
            problem = "Parameterless tool must omit its empty OBJECT schema";
          }
        }
      }
      if (requests.size() > 1) {
        JsonNode firstCall = request.path("contents").get(1).path("parts").get(0);
        if (!"signed-first-tool-turn".equals(firstCall.path("thoughtSignature").asText())) {
          problem = "Function call is missing its thought_signature";
        }
      }
      Object response;
      if (problem != null) {
        response = Map.of("error", Map.of("code", 400, "status", "INVALID_ARGUMENT", "message", problem));
      } else {
        List<Map<String, Object>> parts;
        if (requests.size() == 1) {
          parts = new ArrayList<>(List.of(
              Map.of("functionCall", Map.of("name", "getIncompleteAssessments", "args", Map.of()),
                  "thoughtSignature", "signed-first-tool-turn"),
              Map.of("functionCall", Map.of("name", "getCurrentWorkload", "args", Map.of())),
              Map.of("functionCall", Map.of("name", "getStudyProgress", "args", Map.of()))));
          if (regenerate) parts.add(Map.of("functionCall",
              Map.of("name", "getExistingStudyPlan", "args", Map.of())));
        } else {
          parts = List.of(Map.of("functionCall", Map.of("name", "saveStudyPlan", "args",
              Map.of("action", regenerate ? "REGENERATE" : "GENERATE", "summary", "Reviewed all required tools.", "items", List.of())),
              "thoughtSignature", "signed-final-tool-turn"));
        }
        response = Map.of("candidates", List.of(Map.of("content",
            Map.of("role", "model", "parts", parts), "finishReason", "STOP", "index", 0)),
            "usageMetadata", Map.of("promptTokenCount", 10, "candidatesTokenCount", 20, "totalTokenCount", 30));
      }
      byte[] bytes = json.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(problem == null ? 200 : 400, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      var configured = new ConfiguredPlanningChatModel("gemini", "fixture-key", "test-model", "", "");
      ReflectionTestUtils.setField(configured, "geminiBaseUrl",
          "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
      var tools = mock(PlanningTools.class);
      var dashboard = mock(DashboardQueryService.class);
      when(tools.getIncompleteAssessments("Bearer fixture")).thenReturn(List.of());
      when(dashboard.workload(any(), any())).thenReturn(new WorkloadSummary(0, 0, 0, 0, 0, "LOW"));
      var plans = mock(StudyPlanRepository.class);
      var advisor = new AgenticPlanningAdvisor(configured, tools, dashboard, plans, json);
      LocalDate start = LocalDate.of(2026, 10, 12);
      UUID owner = UUID.randomUUID();
      UUID planId = UUID.randomUUID();
      when(plans.findByIdAndOwnerId(planId, owner)).thenReturn(java.util.Optional.of(
          new au.edu.uow.csci318.planning.domain.StudyPlan(owner, start, start.plusDays(6), 1, "[]", "Original")));
      var request = new PlanRequest(start, Map.of(start, 120));
      var candidate = new StudyPlanningAgent.Schedule(List.of(), start.plusDays(6), 0, 0);
      var advice = regenerate
          ? advisor.adviseRegenerate(owner, "Bearer fixture", ZoneId.of("Australia/Sydney"), request, planId, candidate)
          : advisor.adviseGenerate(owner, "Bearer fixture", ZoneId.of("Australia/Sydney"), request, candidate);
      assertEquals(2, requests.size());
      assertTrue(advice.toolsUsed().containsAll(
          List.of("getIncompleteAssessments", "getCurrentWorkload", "getStudyProgress", "saveStudyPlan")));
      assertEquals("Reviewed all required tools.", advice.summary());
      int results = 0;
      for (JsonNode content : requests.get(1).path("contents")) {
        for (JsonNode part : content.path("parts")) {
          if (part.has("functionResponse")) results++;
        }
      }
      assertEquals(regenerate ? 4 : 3, results);
    } finally {
      server.stop(0);
    }
  }
}
