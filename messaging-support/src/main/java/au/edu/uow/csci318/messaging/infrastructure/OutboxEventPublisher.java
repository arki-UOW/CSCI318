package au.edu.uow.csci318.messaging.infrastructure;

import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.contract.IntegrationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class OutboxEventPublisher implements EventPublisher {
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final String source;

  public OutboxEventPublisher(
      OutboxRepository outbox,
      ObjectMapper json,
      @Value("${spring.application.name}") String source) {
    this.outbox = outbox;
    this.json = json;
    this.source = source;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(
      String binding, String type, UUID ownerId, UUID aggregateId, long revision, Object snapshot) {
    Objects.requireNonNull(ownerId, "An event must belong to an account");
    Objects.requireNonNull(aggregateId, "An event must identify its aggregate");
    if (revision < 1) throw new IllegalArgumentException("Event revision must be positive");
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    IntegrationEvent event =
        new IntegrationEvent(
            id, type, 2, now, source, ownerId, aggregateId, revision, json.valueToTree(snapshot));
    try {
      outbox.save(
          new OutboxMessage(
              id, binding, ownerId + "/" + aggregateId, json.writeValueAsString(event), now));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("The business event could not be serialized", exception);
    }
  }
}
