package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AvailabilityChatRequest;
import au.edu.uow.csci318.planning.dto.PlanningDtos.TimeSlot;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AvailabilityInterpretationTest {
  @Test
  void clearCommandWorksWithoutCallingOrConfiguringAProvider() {
    var configured = mock(ConfiguredPlanningChatModel.class);
    var assistant = new AvailabilityAssistant(new ObjectMapper(), configured);
    LocalDate monday = LocalDate.of(2026, 10, 12);
    var current = Map.of(monday, List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))));
    var result = assistant.assist(new AvailabilityChatRequest(
        "Clear my availability and let me start again.", monday, current));
    assertFalse(result.readyToPlan());
    assertTrue(result.availability().values().stream().allMatch(List::isEmpty));
    assertEquals(0, result.dailyAvailabilityMinutes().values().stream().mapToInt(Integer::intValue).sum());
    verifyNoInteractions(configured);
  }

  private AvailabilityAssistant assistant(String response) {
    var model = mock(ChatModel.class);
    when(model.chat(anyString())).thenReturn(response);
    var configured = mock(ConfiguredPlanningChatModel.class);
    when(configured.selection()).thenReturn(Optional.of(
        new ConfiguredPlanningChatModel.Selection("Gemini", "test-model", model)));
    return new AvailabilityAssistant(new ObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), configured);
  }

  @Test
  void mondayAndWednesdayUseServerCalendarEvenIfModelInventsTuesdayAndThursdayDates() {
    var assistant = assistant("""
        {"reply":"Monday and Wednesday, ready!","readyToPlan":true,"days":[
          {"dayOfWeek":"MONDAY","date":"2026-10-13","slots":[{"start":"18:00","end":"20:00"}]},
          {"dayOfWeek":"WEDNESDAY","date":"2026-10-15","slots":[{"start":"18:00","end":"20:00"}]}
        ]}
        """);
    LocalDate monday = LocalDate.of(2026, 10, 12);
    var result = assistant.assist(new AvailabilityChatRequest(
        "I'm free Monday and Wednesday from 6pm to 8pm.", monday, Map.of()));
    assertEquals(120, result.dailyAvailabilityMinutes().get(monday));
    assertEquals(120, result.dailyAvailabilityMinutes().get(monday.plusDays(2)));
    assertEquals(0, result.dailyAvailabilityMinutes().get(monday.plusDays(1)));
    assertEquals(0, result.dailyAvailabilityMinutes().get(monday.plusDays(3)));
    assertTrue(result.readyToPlan());
    assertTrue(result.reply().contains("Monday 12 Oct"));
    assertTrue(result.reply().contains("Wednesday 14 Oct"));
  }

  @Test
  void weekdayMappingWorksWhenSelectedPeriodStartsOnWednesday() {
    var assistant = assistant("""
        {"reply":"Ready","readyToPlan":true,"days":[
          {"dayOfWeek":"monday","slots":[{"start":"18:00","end":"20:00"}]}]}
        """);
    LocalDate wednesday = LocalDate.of(2026, 10, 14);
    var result = assistant.assist(new AvailabilityChatRequest("Monday 6-8pm", wednesday, Map.of()));
    assertEquals(120, result.dailyAvailabilityMinutes().get(LocalDate.of(2026, 10, 19)));
    assertEquals(120, result.dailyAvailabilityMinutes().values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void clarificationDoesNotApplySpeculativeSlotsOrAllowPlanning() {
    var assistant = assistant("""
        {"reply":"What time on Wednesday?","readyToPlan":false,"days":[
          {"dayOfWeek":"WEDNESDAY","slots":[{"start":"18:00","end":"20:00"}]}]}
        """);
    LocalDate monday = LocalDate.of(2026, 10, 12);
    var current = Map.of(monday, List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))));
    var result = assistant.assist(new AvailabilityChatRequest("Wednesday too", monday, current));
    assertFalse(result.readyToPlan());
    assertEquals(current.get(monday), result.availability().get(monday));
    assertEquals(0, result.dailyAvailabilityMinutes().get(monday.plusDays(2)));
  }

  @Test
  void clearingLastDayIsAppliedEvenThoughNoPlanCanBeGenerated() {
    var assistant = assistant("""
        {"reply":"Cleared Monday.","readyToPlan":false,"days":[
          {"dayOfWeek":"MONDAY","slots":[]}]}
        """);
    LocalDate monday = LocalDate.of(2026, 10, 12);
    var current = Map.of(monday, List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0))));
    var result = assistant.assist(new AvailabilityChatRequest("Clear Monday", monday, current));
    assertFalse(result.readyToPlan());
    assertEquals(0, result.dailyAvailabilityMinutes().values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void unrecognisedWeekdayCannotSilentlyBecomeReady() {
    var assistant = assistant("""
        {"reply":"All done","readyToPlan":true,"days":[
          {"dayOfWeek":"MOONDAY","slots":[{"start":"18:00","end":"20:00"}]}]}
        """);
    assertThrows(ProviderUnavailableException.class, () -> assistant.assist(
        new AvailabilityChatRequest("Monday 6-8pm", LocalDate.of(2026, 10, 12), Map.of())));
  }
}
