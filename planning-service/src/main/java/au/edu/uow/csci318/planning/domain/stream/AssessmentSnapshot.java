package au.edu.uow.csci318.planning.domain.stream;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Planning's local view of an upstream assessment; not the Assessment aggregate itself. */
public record AssessmentSnapshot(
    UUID id,
    UUID subjectId,
    String title,
    String type,
    Double weighting,
    LocalDate dueDate,
    Integer dueWeek,
    String description,
    Integer estimatedMinutes,
    String priority,
    String status,
    Instant updatedAt) {
  public AssessmentSnapshot {
    if (id == null || subjectId == null || title == null || title.isBlank()) {
      throw new IllegalArgumentException("Assessment identity, subject and title are required");
    }
    if (estimatedMinutes != null && estimatedMinutes < 0)
      throw new IllegalArgumentException("Negative workload");
    if (!"INCOMPLETE".equals(status) && !"COMPLETED".equals(status))
      throw new IllegalArgumentException("Invalid status");
  }
}
