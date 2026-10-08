package au.edu.uow.csci318.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyTimeSlotsTest {
  @Test
  void repeatingSlotsSubtractCrossMidnightAndOverlappingCommitments() {
    LocalDate monday = LocalDate.of(2026, 10, 12), nextMonday = monday.plusWeeks(1);
    var slots =
        WeeklyTimeSlots.from(
            monday,
            Map.of(
                monday,
                List.of(new WeeklyTimeSlots.Window(LocalTime.of(9, 0), LocalTime.of(11, 0)))));
    var free =
        slots.free(
            nextMonday,
            List.of(
                new WeeklyTimeSlots.Interval(
                    nextMonday.minusDays(1).atTime(23, 0), nextMonday.atTime(9, 15)),
                new WeeklyTimeSlots.Interval(nextMonday.atTime(10, 0), nextMonday.atTime(10, 30)),
                new WeeklyTimeSlots.Interval(
                    nextMonday.atTime(10, 15), nextMonday.atTime(10, 45))));
    assertEquals(
        List.of(
            new WeeklyTimeSlots.Interval(nextMonday.atTime(9, 15), nextMonday.atTime(10, 0)),
            new WeeklyTimeSlots.Interval(nextMonday.atTime(10, 45), nextMonday.atTime(11, 0))),
        free);
  }

  @Test
  void invalidAndOverlappingSlotsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WeeklyTimeSlots.Window(LocalTime.NOON, LocalTime.of(11, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WeeklyTimeSlots(
                Map.of(
                    DayOfWeek.MONDAY,
                    List.of(
                        new WeeklyTimeSlots.Window(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new WeeklyTimeSlots.Window(LocalTime.of(9, 30), LocalTime.of(11, 0))))));
    LocalDate start = LocalDate.of(2026, 10, 12);
    assertThrows(
        IllegalArgumentException.class,
        () -> WeeklyTimeSlots.from(start, Map.of(start.plusDays(7), List.of())));
  }
}
