package au.edu.uow.csci318.planning.domain.stream;

import java.time.Instant;
import java.util.UUID;

public record ProgressChange(
    UUID ownerId,
    UUID subjectId,
    String sessionKey,
    long revision,
    boolean deleted,
    SessionSnapshot session,
    SubjectSnapshot subject,
    Instant occurredAt) {
  public String key() {
    return ownerId + "/" + subjectId;
  }
}
