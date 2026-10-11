package au.edu.uow.csci318.planning.application;

import static au.edu.uow.csci318.planning.domain.CalendarEntry.EntryType.STUDY_SESSION;
import static au.edu.uow.csci318.planning.domain.CalendarEntry.Origin.MANUAL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.domain.WeeklyTimeSlots;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanItem;
import au.edu.uow.csci318.planning.infrastructure.CalendarEntryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarApplicationServiceTest {
  @Mock CalendarEntryRepository entries;

  private CalendarEntry busy(UUID owner, LocalDateTime from, LocalDateTime to) {
    return new CalendarEntry(
        owner,
        null,
        null,
        null,
        null,
        "Commitment",
        "",
        CalendarEntry.EntryType.TASK,
        from,
        to,
        MANUAL,
        false,
        0);
  }

  private List<CalendarEntry> place(UUID owner, LocalDate date, int minutes) {
    new CalendarApplicationService(
            entries, mock(CompletedStudyBlockEvents.class), mock(CalendarReferences.class))
        .replaceAiPlan(
            owner,
            UUID.randomUUID(),
            List.of(new PlanItem(date, UUID.randomUUID(), UUID.randomUUID(), "AI", minutes, 0)));
    ArgumentCaptor<CalendarEntry> saved = ArgumentCaptor.forClass(CalendarEntry.class);
    verify(entries, atLeastOnce()).save(saved.capture());
    return saved.getAllValues();
  }

  @Test
  void minutesOnlyPlanningMovesPastExistingEveningCommitment() {
    UUID owner = UUID.randomUUID();
    LocalDate day = LocalDate.of(2026, 10, 12);
    when(entries.findOverlapping(eq(owner), any(), any()))
        .thenReturn(List.of(busy(owner, day.atTime(18, 0), day.atTime(19, 0))));
    var saved = place(owner, day, 60);
    assertEquals(1, saved.size());
    assertEquals(day.atTime(19, 0), saved.getFirst().getStartAt());
    assertEquals(day.atTime(20, 0), saved.getFirst().getEndAt());
  }

  @Test
  void minutesOnlyPlanningUsesEarlierFreeTimeAndRespectsOvernightCommitments() {
    UUID owner = UUID.randomUUID();
    LocalDate day = LocalDate.of(2026, 10, 12);
    when(entries.findOverlapping(eq(owner), any(), any()))
        .thenReturn(
            List.of(
                busy(owner, day.minusDays(1).atTime(23, 0), day.atTime(1, 0)),
                busy(owner, day.atTime(18, 0), day.plusDays(1).atStartOfDay())));
    var saved = place(owner, day, 60);
    assertEquals(day.atTime(1, 0), saved.getFirst().getStartAt());
    assertEquals(day.atTime(2, 0), saved.getFirst().getEndAt());
  }

  @Test
  void minutesOnlyPlanningCanUseFullTwentyFourHourCapacity() {
    UUID owner = UUID.randomUUID();
    LocalDate day = LocalDate.of(2026, 10, 12);
    when(entries.findOverlapping(eq(owner), any(), any())).thenReturn(List.of());
    var saved = place(owner, day, 1440);
    assertEquals(day.atStartOfDay(), saved.getFirst().getStartAt());
    assertEquals(day.plusDays(1).atStartOfDay(), saved.getFirst().getEndAt());
  }

  @Test
  void minutesOnlyConflictPreservesPreviousCalendarWithoutPartialWrites() {
    UUID owner = UUID.randomUUID();
    LocalDate day = LocalDate.of(2026, 10, 12);
    when(entries.findOverlapping(eq(owner), any(), any()))
        .thenReturn(List.of(busy(owner, day.atStartOfDay(), day.plusDays(1).atStartOfDay())));
    assertThrows(IllegalArgumentException.class, () -> place(owner, day, 60));
    verify(entries, never()).deleteByOwnerIdAndOriginAndStatus(any(), any(), any());
    verify(entries, never()).save(any());
  }

  @Test
  void planBlocksStayInSlotsAndSplitAroundExistingCommitments() {
    LocalDate monday = LocalDate.of(2026, 10, 12), date = monday.plusWeeks(1);
    UUID owner = UUID.randomUUID();
    var busy =
        new CalendarEntry(
            owner,
            null,
            null,
            null,
            null,
            "Lecture",
            "",
            CalendarEntry.EntryType.TASK,
            date.atTime(9, 20),
            date.atTime(9, 40),
            MANUAL,
            false,
            0);
    when(entries.findOverlapping(eq(owner), any(), any())).thenReturn(List.of(busy));
    var slots =
        WeeklyTimeSlots.from(
            monday,
            Map.of(
                monday,
                List.of(
                    new WeeklyTimeSlots.Window(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                    new WeeklyTimeSlots.Window(LocalTime.of(14, 0), LocalTime.of(15, 0)))));
    new CalendarApplicationService(
            entries, mock(CompletedStudyBlockEvents.class), mock(CalendarReferences.class))
        .replaceAiPlan(
            owner,
            UUID.randomUUID(),
            List.of(new PlanItem(date, UUID.randomUUID(), UUID.randomUUID(), "AI", 60, 0)),
            slots);
    ArgumentCaptor<CalendarEntry> saved = ArgumentCaptor.forClass(CalendarEntry.class);
    verify(entries, times(3)).save(saved.capture());
    assertEquals(
        List.of(date.atTime(9, 0), date.atTime(9, 40), date.atTime(14, 0)),
        saved.getAllValues().stream().map(CalendarEntry::getStartAt).toList());
    assertEquals(
        List.of(date.atTime(9, 20), date.atTime(10, 0), date.atTime(14, 20)),
        saved.getAllValues().stream().map(CalendarEntry::getEndAt).toList());
  }

  @Test
  void aCalendarConflictDoesNotDeleteOrPartiallyReplaceThePreviousPlan() {
    LocalDate day = LocalDate.of(2026, 10, 12);
    UUID owner = UUID.randomUUID();
    var busy =
        new CalendarEntry(
            owner,
            null,
            null,
            null,
            null,
            "Lecture",
            "",
            CalendarEntry.EntryType.TASK,
            day.atTime(9, 30),
            day.atTime(10, 0),
            MANUAL,
            false,
            0);
    when(entries.findOverlapping(eq(owner), any(), any())).thenReturn(List.of(busy));
    var slots =
        WeeklyTimeSlots.from(
            day,
            Map.of(
                day, List.of(new WeeklyTimeSlots.Window(LocalTime.of(9, 0), LocalTime.of(10, 0)))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CalendarApplicationService(
                    entries, mock(CompletedStudyBlockEvents.class), mock(CalendarReferences.class))
                .replaceAiPlan(
                    owner,
                    UUID.randomUUID(),
                    List.of(new PlanItem(day, UUID.randomUUID(), UUID.randomUUID(), "AI", 60, 0)),
                    slots));
    verify(entries, never()).deleteByOwnerIdAndOriginAndStatus(any(), any(), any());
    verify(entries, never()).save(any());
  }

  @Test
  void completionCreatesFirstSpacedReviewForTomorrow() {
    UUID ownerId = UUID.randomUUID();
    LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(18, 0));
    CalendarEntry original =
        new CalendarEntry(
            ownerId,
            UUID.randomUUID(),
            null,
            null,
            null,
            "x".repeat(160),
            "",
            STUDY_SESSION,
            start,
            start.plusMinutes(45),
            MANUAL,
            true,
            0);
    when(entries.findByIdAndOwnerId(original.getId(), ownerId)).thenReturn(Optional.of(original));
    when(entries.save(any(CalendarEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CompletedStudyBlockEvents events = mock(CompletedStudyBlockEvents.class);
    var result =
        new CalendarApplicationService(entries, events, mock(CalendarReferences.class))
            .complete(ownerId, original.getId(), java.time.ZoneId.of("UTC"));
    verify(events).publish(original, false, java.time.ZoneId.of("UTC"));

    assertNotNull(result.nextReview());
    assertEquals(160, result.nextReview().title().length());
    assertTrue(result.nextReview().description().contains("x".repeat(160)));
    assertEquals(CalendarEntry.EntryStatus.COMPLETED, result.completed().status());
    assertEquals(
        LocalDate.now(java.time.ZoneId.of("UTC")).plusDays(1),
        result.nextReview().startAt().toLocalDate());
    assertEquals(1, result.nextReview().repetitionStage());
    assertEquals(
        45,
        java.time.Duration.between(result.nextReview().startAt(), result.nextReview().endAt())
            .toMinutes());
    ArgumentCaptor<CalendarEntry> saved = ArgumentCaptor.forClass(CalendarEntry.class);
    verify(entries).save(saved.capture());
    assertEquals(original.getId(), saved.getValue().getSourceEntryId());
  }
}
