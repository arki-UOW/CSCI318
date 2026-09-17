package au.edu.uow.csci318.messaging.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "event_outbox",
    indexes = @Index(name = "idx_outbox_created", columnList = "created_at"))
public class OutboxMessage {
  @Id private UUID id;

  @Column(nullable = false)
  private String binding;

  @Column(nullable = false)
  private String messageKey;

  @Lob
  @Column(nullable = false, columnDefinition = "CLOB")
  private String body;

  @Column(nullable = false)
  private Instant createdAt;

  protected OutboxMessage() {}

  OutboxMessage(UUID id, String binding, String key, String body, Instant createdAt) {
    this.id = id;
    this.binding = binding;
    this.messageKey = key;
    this.body = body;
    this.createdAt = createdAt;
  }

  public String getBinding() {
    return binding;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getBody() {
    return body;
  }
}
