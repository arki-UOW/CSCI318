package au.edu.uow.csci318.planning.domain.stream;

import java.time.Instant;
import java.util.UUID;

public record AssessmentChange(
    UUID ownerId,
    long revision,
    boolean deleted,
    AssessmentSnapshot snapshot,
    Instant occurredAt) {}
