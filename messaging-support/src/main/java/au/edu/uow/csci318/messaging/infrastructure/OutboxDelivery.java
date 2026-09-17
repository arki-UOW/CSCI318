package au.edu.uow.csci318.messaging.infrastructure;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDelivery {
  private final OutboxRepository outbox;
  private final StreamBridge kafka;

  public OutboxDelivery(OutboxRepository outbox, StreamBridge kafka) {
    this.outbox = outbox;
    this.kafka = kafka;
  }

  @Transactional
  public void deliverBatch() {
    for (OutboxMessage message : outbox.pending(PageRequest.of(0, 50))) {
      boolean sent =
          kafka.send(
              message.getBinding(),
              MessageBuilder.withPayload(message.getBody())
                  .setHeader("contentType", "application/json")
                  .setHeader("eventKey", message.getMessageKey())
                  .build());
      if (!sent) throw new IllegalStateException("Kafka did not accept the outbox event");
      outbox.delete(message);
    }
  }
}
