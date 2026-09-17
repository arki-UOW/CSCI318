package au.edu.uow.csci318.messaging.application;

import java.util.UUID;

/** Port used by application use cases. Kafka and serialization stay behind this boundary. */
public interface EventPublisher {
  void publish(
      String binding, String type, UUID ownerId, UUID aggregateId, long revision, Object snapshot);
}
