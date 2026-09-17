package au.edu.uow.csci318.planning.domain.stream;

import java.util.UUID;

public record SubjectSnapshot(UUID id, String code, String name, int weeklyStudyTargetMinutes) {
  public SubjectSnapshot {
    if (id == null
        || code == null
        || name == null
        || weeklyStudyTargetMinutes < 0
        || weeklyStudyTargetMinutes > 10080)
      throw new IllegalArgumentException("Invalid subject snapshot");
  }
}
