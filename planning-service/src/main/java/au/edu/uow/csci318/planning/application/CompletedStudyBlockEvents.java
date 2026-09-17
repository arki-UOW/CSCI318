package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.application.SnapshotSource;
import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.infrastructure.CalendarEntryRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompletedStudyBlockEvents implements SnapshotSource {
  private final EventPublisher events;
  private final CalendarEntryRepository entries;

  public CompletedStudyBlockEvents(EventPublisher events, CalendarEntryRepository entries) {
    this.events = events;
    this.entries = entries;
  }

  public void publish(CalendarEntry entry, boolean deleted, ZoneId zone) {
    if (entry.getStatus() != CalendarEntry.EntryStatus.COMPLETED
        || entry.getSubjectId() == null
        || entry.getType() == CalendarEntry.EntryType.TASK) return;
    LocalDate date = entry.getCompletedAt().atZone(zone).toLocalDate();
    events.publish(
        "planningEvents-out-0",
        deleted ? "StudyBlockDeleted" : "StudyBlockCompleted",
        entry.getOwnerId(),
        entry.getId(),
        entry.getEventRevision() + (deleted ? 1 : 0),
        new Snapshot(
            entry.getId(),
            entry.getSubjectId(),
            entry.getAssessmentId(),
            (int) Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes(),
            date,
            entry.getTitle()));
  }

  @Override
  public String migrationKey() {
    return "completed-study-block-events-v2";
  }

  @Override
  public void enqueueSnapshots() {
    entries.findAll().forEach(entry -> publish(entry, false, ZoneId.of("UTC")));
  }

  public record Snapshot(
      UUID id,
      UUID subjectId,
      UUID assessmentId,
      int durationMinutes,
      LocalDate studyDate,
      String description) {}
}
