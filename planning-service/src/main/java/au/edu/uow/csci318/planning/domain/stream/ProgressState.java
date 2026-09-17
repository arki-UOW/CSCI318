package au.edu.uow.csci318.planning.domain.stream;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Session table + weekly aggregation + subject-target stream merge. Edits replace, never add twice.
 */
public record ProgressState(
    UUID ownerId,
    UUID subjectId,
    SubjectSnapshot subject,
    long subjectRevision,
    Map<String, SessionFact> sessions,
    int totalMinutes,
    Map<LocalDate, Integer> weeklyMinutes,
    Map<LocalDate, Integer> weeklySessionCounts,
    long revision,
    Instant updatedAt) {
  public record SessionFact(long revision, boolean deleted, SessionSnapshot snapshot) {}

  public static ProgressState empty() {
    return new ProgressState(
        null, null, null, 0, Map.of(), 0, Map.of(), Map.of(), 0, Instant.EPOCH);
  }

  public ProgressState apply(ProgressChange change) {
    if (ownerId != null
        && (!ownerId.equals(change.ownerId()) || !subjectId.equals(change.subjectId()))) {
      throw new IllegalArgumentException("Progress key mismatch");
    }
    SubjectSnapshot nextSubject = subject;
    long targetRevision = subjectRevision;
    Map<String, SessionFact> next = new HashMap<>(sessions);
    if (change.subject() != null) {
      if (subjectRevision >= change.revision()) return this;
      nextSubject = change.subject();
      targetRevision = change.revision();
    } else {
      SessionFact previous = next.get(change.sessionKey());
      if (previous != null && previous.revision() >= change.revision()) return this;
      next.put(
          change.sessionKey(),
          new SessionFact(change.revision(), change.deleted(), change.session()));
    }
    Map<LocalDate, Integer> minutes = new HashMap<>();
    Map<LocalDate, Integer> counts = new HashMap<>();
    int total = 0;
    for (SessionFact fact : next.values()) {
      if (fact.deleted()) continue;
      SessionSnapshot session = fact.snapshot();
      LocalDate week = session.studyDate().with(DayOfWeek.MONDAY);
      minutes.merge(week, session.durationMinutes(), Integer::sum);
      counts.merge(week, 1, Integer::sum);
      total += session.durationMinutes();
    }
    return new ProgressState(
        change.ownerId(),
        change.subjectId(),
        nextSubject,
        targetRevision,
        Map.copyOf(next),
        total,
        Map.copyOf(minutes),
        Map.copyOf(counts),
        revision + 1,
        change.occurredAt().isAfter(updatedAt) ? change.occurredAt() : updatedAt);
  }
}
