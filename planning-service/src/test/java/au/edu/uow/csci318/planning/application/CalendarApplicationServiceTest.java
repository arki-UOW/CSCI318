package au.edu.uow.csci318.planning.application;

import static au.edu.uow.csci318.planning.domain.CalendarEntry.EntryType.STUDY_SESSION;
import static au.edu.uow.csci318.planning.domain.CalendarEntry.Origin.MANUAL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.infrastructure.CalendarEntryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            "Review lecture notes",
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
        new CalendarApplicationService(entries, events)
            .complete(ownerId, original.getId(), java.time.ZoneId.of("UTC"));
    verify(events).publish(original, false, java.time.ZoneId.of("UTC"));

    assertNotNull(result.nextReview());
    assertEquals(LocalDate.now().plusDays(1), result.nextReview().startAt().toLocalDate());
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
