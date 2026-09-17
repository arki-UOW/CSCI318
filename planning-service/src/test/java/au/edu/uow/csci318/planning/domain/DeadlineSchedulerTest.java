package au.edu.uow.csci318.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.planning.domain.stream.AssessmentSnapshot;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DeadlineSchedulerTest {
  private final LocalDate start = LocalDate.of(2026, 9, 21);
  private final UUID assessment = UUID.randomUUID();
  private final DeadlineScheduler scheduler = new DeadlineScheduler();

  private AssessmentSnapshot work(int minutes, int days) {
    return work(assessment, minutes, days);
  }

  private AssessmentSnapshot work(UUID id, int minutes, int days) {
    return new AssessmentSnapshot(
        id,
        UUID.randomUUID(),
        "Exam",
        "Exam",
        50.0,
        start.plusDays(days),
        null,
        null,
        minutes,
        "HIGH",
        "INCOMPLETE",
        Instant.EPOCH);
  }

  private WeeklyAvailability capacity(int minutes) {
    Map<DayOfWeek, Integer> week = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) week.put(day, minutes);
    return new WeeklyAvailability(week);
  }

  @Test
  void multipleBlocksCanUseOneDayAndOnlyTrueCapacityShortfallsAreReported() {
    var fitting = scheduler.schedule(start, capacity(360), List.of(work(300, 1)), Map.of());
    assertEquals(300, fitting.scheduledMinutes());
    assertEquals(0, fitting.unscheduledMinutes());
    assertTrue(fitting.items().stream().allMatch(item -> item.allocatedMinutes() <= 90));
    var shortage = scheduler.schedule(start, capacity(120), List.of(work(300, 1)), Map.of());
    assertEquals(120, shortage.scheduledMinutes());
    assertEquals(180, shortage.unscheduledMinutes());
  }

  @Test
  void completedLinkedMinutesAreSubtractedOnRegeneration() {
    var result =
        scheduler.schedule(start, capacity(120), List.of(work(180, 45)), Map.of(assessment, 60));
    assertEquals(120, result.requestedMinutes());
    assertEquals(120, result.scheduledMinutes());
    assertTrue(result.items().stream().anyMatch(item -> item.date().isAfter(start.plusDays(30))));
  }

  @Test
  void longPlansNeverExceedDailyCapacityOrAnyDeadline() {
    var result =
        scheduler.schedule(
            start,
            capacity(60),
            List.of(work(600, 90), work(UUID.randomUUID(), 300, 14)),
            Map.of());
    Map<LocalDate, Integer> days = new HashMap<>();
    result.items().forEach(item -> days.merge(item.date(), item.allocatedMinutes(), Integer::sum));
    assertTrue(days.values().stream().allMatch(minutes -> minutes <= 60));
    assertTrue(
        result.items().stream()
            .allMatch(
                item -> !item.date().isBefore(start) && item.date().isBefore(result.endDate())));
    assertEquals(900, result.scheduledMinutes());
  }

  @Test
  void unsupportedHorizonIsExplicitRatherThanSilentlyTruncated() {
    assertThrows(
        IllegalArgumentException.class,
        () -> scheduler.schedule(start, capacity(60), List.of(work(180, 400)), Map.of()));
  }
}
