package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvailabilityAssistantTest {
    @Test
    void normalisesACompleteWeekAndCalculatesValidNonOverlappingMinutes() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        var updates = List.of(new AvailabilityAssistant.AvailabilityDay(monday, List.of(
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 30)),
                new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0)),
                new TimeSlot(LocalTime.of(15, 0), LocalTime.of(15, 10)))));

        Map<LocalDate, List<TimeSlot>> result = AvailabilityAssistant.normalise(
                monday, Map.of(monday.plusDays(1), List.of(
                        new TimeSlot(LocalTime.of(18, 0), LocalTime.of(19, 0)))), updates);

        assertEquals(7, result.size());
        assertEquals(90, AvailabilityAssistant.dailyMinutes(result).get(monday));
        assertEquals(60, AvailabilityAssistant.dailyMinutes(result).get(monday.plusDays(1)));
    }
}
