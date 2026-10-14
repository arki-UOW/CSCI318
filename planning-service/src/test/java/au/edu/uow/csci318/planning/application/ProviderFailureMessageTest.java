package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderFailureMessageTest {
  @ParameterizedTest
  @CsvSource({"400,request format", "401,key was rejected", "403,key was rejected",
      "404,model", "429,quota", "500,temporarily unavailable", "503,temporarily unavailable"})
  void reportsUpstreamStatusWithoutLeakingProviderBody(int status, String guidance) {
    var selection = new ConfiguredPlanningChatModel.Selection("Gemini", "test-model", null);
    var failure = new RuntimeException(new HttpException(status, "secret-value private-document"));
    String message = ConfiguredPlanningChatModel.failureMessage(selection, failure, "generate a plan");
    assertTrue(message.contains("Upstream HTTP " + status), message);
    assertTrue(message.contains(guidance), message);
    assertFalse(message.contains("secret-value"));
    assertFalse(message.contains("private-document"));
  }
}
