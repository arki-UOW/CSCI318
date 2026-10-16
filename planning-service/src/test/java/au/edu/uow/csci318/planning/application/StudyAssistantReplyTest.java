package au.edu.uow.csci318.planning.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import au.edu.uow.csci318.planning.dto.AssistantDtos.ChatRequest;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
class StudyAssistantReplyTest {
  private final ObjectMapper json = new ObjectMapper();
  @Test void unwrapsEnvelopesAndPreservesMath() throws Exception {
    String answer = "Solve \\(ax^2 + bx + c = 0\\).\n\nUse \\[x=\\frac{-b}{2a}\\]";
    String wrapped = json.writeValueAsString(Map.of("reply", answer));
    assertEquals(answer, StudyAssistantService.readReply("```json\n" + wrapped + "\n```", json));
    assertEquals(answer, StudyAssistantService.readReply(json.writeValueAsString(Map.of("reply", wrapped)), json));
  }
  @Test void invalidJsonAndControlCharactersNeverBecomeStudentText() {
    assertThrows(Exception.class, () -> StudyAssistantService.readReply("{\"reply\":\"Use \\(x\\)\"}", json));
    assertThrows(Exception.class, () -> StudyAssistantService.readReply("{\"reply\":\"Use \\frac{1}{2}\"}", json));
    assertThrows(Exception.class, () -> StudyAssistantService.readReply("{\"reply\":42}", json));
  }
  @Test void malformedProviderReplyGetsOneRepairAttemptThenClearFailure() throws Exception {
    var model=mock(ChatModel.class);var configured=mock(ConfiguredPlanningChatModel.class);
    when(configured.selection()).thenReturn(Optional.of(new ConfiguredPlanningChatModel.Selection("Gemini","fixture",model)));
    var tools=mock(PlanningTools.class);var calendar=mock(CalendarApplicationService.class);
    var service=new StudyAssistantService(configured,tools,calendar,json);
    when(model.chat(anyString())).thenReturn("{bad json", "{\"reply\":\"Try factoring.\"}");
    assertEquals("Try factoring.",service.chat(UUID.randomUUID(),"Bearer fixture",new ChatRequest("Quadratics",List.of()),ZoneOffset.UTC).reply());
    verify(model,times(2)).chat(anyString());
    reset(model);when(model.chat(anyString())).thenReturn("{bad json");
    var error=assertThrows(ProviderUnavailableException.class,()->service.chat(UUID.randomUUID(),"Bearer fixture",new ChatRequest("Quadratics",List.of()),ZoneOffset.UTC));
    assertTrue(error.getMessage().contains("unreadable answer"));verify(model,times(2)).chat(anyString());
  }
}
