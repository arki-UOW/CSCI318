package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiRetryTransportTest {
  @ParameterizedTest
  @CsvSource({"503, 2, 3, true", "503, 9, 3, false", "400, 9, 1, false"})
  void actualSdkRecoversTransientFailuresButBoundsRetries(
      int status, int failures, int expectedCalls, boolean success) throws Exception {
    var calls = new AtomicInteger();
    var json = new ObjectMapper();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
      exchange.getRequestBody().readAllBytes();
      boolean failed = calls.incrementAndGet() <= failures;
      Object response = failed
          ? Map.of("error", Map.of("code", status, "message", "Fixture provider failure"))
          : Map.of("candidates", List.of(Map.of("content", Map.of("role", "model",
              "parts", List.of(Map.of("text", "recovered"))), "finishReason", "STOP", "index", 0)),
              "usageMetadata", Map.of("promptTokenCount", 1, "candidatesTokenCount", 1, "totalTokenCount", 2));
      byte[] body = json.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(failed ? status : 200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      var configured = new ConfiguredPlanningChatModel("gemini", "fixture-key", "test-model", "", "");
      ReflectionTestUtils.setField(configured, "geminiBaseUrl",
          "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
      var model = configured.selection().orElseThrow().model();
      if (success) assertEquals("recovered", model.chat("Test retry transport"));
      else assertThrows(RuntimeException.class, () -> model.chat("Test retry transport"));
      assertEquals(expectedCalls, calls.get());
    } finally {
      server.stop(0);
    }
  }
}
