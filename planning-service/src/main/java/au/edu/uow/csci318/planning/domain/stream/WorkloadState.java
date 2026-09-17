package au.edu.uow.csci318.planning.domain.stream;

import java.time.Instant;
import java.util.*;

/** Immutable, keyed stateful reduction. Revisioned tombstones prevent replay resurrection. */
public record WorkloadState(
    UUID ownerId,
    Map<UUID, AssessmentFact> assessments,
    long revision,
    Instant updatedAt,
    int incompleteCount,
    int estimatedMinutes,
    int highPriorityCount) {
  public record AssessmentFact(long revision, boolean deleted, AssessmentSnapshot snapshot) {}

  public static WorkloadState empty() {
    return new WorkloadState(null, Map.of(), 0, Instant.EPOCH, 0, 0, 0);
  }

  public WorkloadState apply(AssessmentChange change) {
    if (ownerId != null && !ownerId.equals(change.ownerId()))
      throw new IllegalArgumentException("Owner mismatch");
    AssessmentFact previous = assessments.get(change.snapshot().id());
    if (previous != null && previous.revision() >= change.revision()) return this;
    Map<UUID, AssessmentFact> next = new HashMap<>(assessments);
    next.put(
        change.snapshot().id(),
        new AssessmentFact(change.revision(), change.deleted(), change.snapshot()));
    List<AssessmentSnapshot> outstanding =
        next.values().stream()
            .filter(fact -> !fact.deleted())
            .map(AssessmentFact::snapshot)
            .filter(item -> "INCOMPLETE".equals(item.status()))
            .toList();
    int minutes =
        outstanding.stream()
            .map(AssessmentSnapshot::estimatedMinutes)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
    int high = (int) outstanding.stream().filter(item -> "HIGH".equals(item.priority())).count();
    return new WorkloadState(
        change.ownerId(),
        Map.copyOf(next),
        revision + 1,
        change.occurredAt().isAfter(updatedAt) ? change.occurredAt() : updatedAt,
        outstanding.size(),
        minutes,
        high);
  }

  public List<AssessmentSnapshot> outstanding() {
    return assessments.values().stream()
        .filter(fact -> !fact.deleted())
        .map(AssessmentFact::snapshot)
        .filter(item -> "INCOMPLETE".equals(item.status()))
        .toList();
  }
}
