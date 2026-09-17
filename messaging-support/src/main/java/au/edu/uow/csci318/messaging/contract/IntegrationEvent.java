package au.edu.uow.csci318.messaging.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Versioned public integration contract, not an aggregate shared between bounded contexts. */
public record IntegrationEvent(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant eventTimestamp,
    String sourceService,
    UUID ownerId,
    UUID aggregateId,
    long aggregateRevision,
    JsonNode payload) {}
