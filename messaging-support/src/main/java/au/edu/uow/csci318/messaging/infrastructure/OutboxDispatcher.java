package au.edu.uow.csci318.messaging.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
  private final OutboxDelivery delivery;

  public OutboxDispatcher(OutboxDelivery delivery) {
    this.delivery = delivery;
  }

  @Scheduled(fixedDelayString = "${study.events.delivery-delay-ms:1000}")
  public void deliver() {
    try {
      delivery.deliverBatch();
    } catch (Exception exception) {
      // Leave rows intact for retry; never log event bodies, credentials or tokens.
      LOG.warn(
          "Event delivery deferred; committed outbox rows will be retried ({})",
          exception.getClass().getSimpleName());
    }
  }
}
