package au.edu.uow.csci318.planning.domain.stream;

import java.time.LocalDate;
import java.util.UUID;

public record SessionSnapshot(
    UUID id,
    UUID subjectId,
    UUID assessmentId,
    int durationMinutes,
    LocalDate studyDate,
    String description) {
  public SessionSnapshot {
    if (id == null
        || subjectId == null
        || studyDate == null
        || durationMinutes <= 0
        || durationMinutes > 1440) {
      throw new IllegalArgumentException("Invalid study session snapshot");
    }
  }
}
