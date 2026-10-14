package au.edu.uow.csci318.subject.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.subject.application.SubjectApplicationService.ServiceDependencyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Exercises the real Gemini SDK transport against local Google-format HTTP responses. */
class GeminiOutlineTransportTest {
  private final ObjectMapper json = new ObjectMapper();
  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  private final AtomicReference<String> body = new AtomicReference<>();

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private SafeOutlineExtractor extractor(int status, boolean recover) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      int attempt = requests.incrementAndGet();
      int responseStatus = recover && attempt > 1 ? 200 : status;
      String response = responseStatus == 200
          ? "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":"
              + json.writeValueAsString("""
                  {"subjectCode":"CSCI318","subjectName":"Software Engineering","creditPoints":6,"assessments":[],"warnings":[]}
                  """)
              + "}]},\"finishReason\":\"STOP\",\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":20,\"totalTokenCount\":30}}"
          : "{\"error\":{\"code\":" + responseStatus
              + ",\"message\":\"Private request context configured-test-key\",\"status\":\""
              + (responseStatus == 503 ? "UNAVAILABLE" : "INVALID_ARGUMENT") + "\"}}";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(responseStatus, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    var configured = new ConfiguredChatModel("gemini", "configured-test-key", "test-model", "", "unused");
    ReflectionTestUtils.setField(configured, "geminiBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
    return new SafeOutlineExtractor(json, configured);
  }

  @Test
  void retriesTemporaryGoogleFailureAndParsesSuccessfulResponse() throws Exception {
    var result = extractor(503, true).extract("CSCI318 Software Engineering");
    assertEquals("CSCI318", result.subjectCode());
    assertEquals(2, requests.get());
    var request = json.readTree(body.get());
    assertEquals("application/json", request.at("/generationConfig/responseMimeType").asText());
    assertFalse(request.path("generationConfig").hasNonNull("temperature"));
    assertTrue(body.get().contains("CSCI318 Software Engineering"));
  }

  @Test
  void exhaustedRetriesGiveSafeTemporaryFailureWithoutFakeExtraction() throws Exception {
    var extractor = extractor(503, false);
    var error = assertThrows(ServiceDependencyException.class, () -> extractor.extract("CSCI318 Software Engineering"));
    assertEquals(3, requests.get());
    assertTrue(error.getMessage().contains("temporarily unavailable"), error.getMessage());
    assertTrue(error.getMessage().contains("503"), error.getMessage());
    assertFalse(error.getMessage().contains("configured-test-key"));
    assertFalse(error.getMessage().contains("Private request context"));
  }

  @Test
  void rejectedRequestIsNotRetriedOrMisreportedAsBadKey() throws Exception {
    var extractor = extractor(400, false);
    var error = assertThrows(ServiceDependencyException.class, () -> extractor.extract("CSCI318 Software Engineering"));
    assertEquals(1, requests.get());
    assertTrue(error.getMessage().contains("request format"), error.getMessage());
    assertTrue(error.getMessage().contains("400"), error.getMessage());
    assertFalse(error.getMessage().contains("configured-test-key"));
  }
}
