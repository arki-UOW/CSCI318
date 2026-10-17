package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanningTimePolicyTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 10, 16, 17, 30);

  @Test void rejectsPreviousMondayAndElapsedSlotsButAcceptsUpcomingMonday() {
    assertThrows(IllegalArgumentException.class, () -> PlanningTimePolicy.validate(
        new PlanRequest(LocalDate.of(2026, 10, 12), Map.of()), now));
    assertThrows(IllegalArgumentException.class, () -> PlanningTimePolicy.validate(
        new PlanRequest(now.toLocalDate(), Map.of(now.toLocalDate(), 60),
            Map.of(now.toLocalDate(), List.of(new TimeSlot(LocalTime.of(16, 0), LocalTime.of(17, 0))))), now));
    assertDoesNotThrow(() -> PlanningTimePolicy.validate(new PlanRequest(now.toLocalDate(),
        Map.of(LocalDate.of(2026, 10, 19), 120), Map.of(LocalDate.of(2026, 10, 19),
            List.of(new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0))))), now));
  }

  @Test void allowsZeroWorkWithoutAvailabilityAndRejectsImpossibleTodayMinutes() {
    assertDoesNotThrow(() -> PlanningTimePolicy.validate(new PlanRequest(now.toLocalDate(), Map.of()), now));
    assertThrows(IllegalArgumentException.class, () -> PlanningTimePolicy.validate(
        new PlanRequest(now.toLocalDate(), Map.of(now.toLocalDate(), 391)), now));
  }

  @Test void accountTimezoneDeterminesTodayAcrossMidnight() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-15T14:00:00Z"), ZoneOffset.UTC);
    var sydney = LocalDateTime.now(clock.withZone(ZoneId.of("Australia/Sydney")));
    assertEquals(LocalDate.of(2026, 10, 16), sydney.toLocalDate());
    assertThrows(IllegalArgumentException.class, () -> PlanningTimePolicy.requireFutureStart(LocalDate.of(2026, 10, 15), sydney));
  }
  @Test void elapsedFridayDoesNotRejectValidMondayToThursdayAvailability() {
    var friday=LocalDate.of(2026,10,16);
    var slot=new TimeSlot(LocalTime.of(16,0), LocalTime.of(22,0));
    var availability=Map.of(friday,List.of(slot),friday.plusDays(3),List.of(slot));
    var result=AvailabilityAssistant.futureOnly(new AvailabilityChatResponse("Ready",availability,
        AvailabilityAssistant.dailyMinutes(availability),true,"Gemini"), friday.atTime(19,30,20));
    assertTrue(result.readyToPlan());assertEquals(149,result.dailyAvailabilityMinutes().get(friday));
    assertEquals(360,result.dailyAvailabilityMinutes().get(friday.plusDays(3)));
    assertTrue(result.reply().contains("Elapsed time was removed"));
    assertDoesNotThrow(()->PlanningTimePolicy.validate(new PlanRequest(friday,result.dailyAvailabilityMinutes(),result.availability()),friday.atTime(19,30,20)));
  }
  @Test void whollyElapsedSlotsAreRemovedAndClarificationNeverBecomesReady() {
    var date=now.toLocalDate();var slots=Map.of(date,List.of(new TimeSlot(LocalTime.of(8,0),LocalTime.of(9,0))));
    var result=AvailabilityAssistant.futureOnly(new AvailabilityChatResponse("Ready",slots,Map.of(date,60),true,"Gemini"),now);
    assertFalse(result.readyToPlan());assertEquals(0,result.dailyAvailabilityMinutes().get(date));
    var future=Map.of(date.plusDays(1),List.of(new TimeSlot(LocalTime.of(18,0),LocalTime.of(20,0))));
    assertFalse(AvailabilityAssistant.futureOnly(new AvailabilityChatResponse("Which day?",future,Map.of(),false,"Gemini"),now).readyToPlan());
  }
}
