package au.edu.uow.csci318.messaging.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.application.SnapshotSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "spring.application.name=outbox-test")
@ContextConfiguration(classes = OutboxTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxTest {
  @Configuration
  @EntityScan(basePackageClasses = OutboxMessage.class)
  @EnableJpaRepositories(basePackageClasses = OutboxRepository.class)
  @Import({OutboxEventPublisher.class, OutboxDelivery.class, SnapshotMigration.class})
  static class Config {
    @Bean
    ObjectMapper json() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }

  @Autowired EventPublisher publisher;
  @Autowired OutboxRepository outbox;
  @Autowired OutboxDelivery delivery;
  @Autowired SnapshotMigration migration;
  @Autowired EventMigrationRepository migrations;
  @Autowired PlatformTransactionManager transactions;
  @MockitoBean StreamBridge kafka;

  @BeforeEach
  void clear() {
    outbox.deleteAll();
    migrations.deleteAll();
  }

  private void enqueue(UUID owner, UUID id) {
    publisher.publish(
        "assessmentEvents-out-0", "AssessmentCreated", owner, id, 1, Map.of("id", id));
  }

  @Test
  void rollbackLeavesNoEventAndCommittedEventsAreRetryable() {
    UUID owner = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    TransactionTemplate transaction = new TransactionTemplate(transactions);
    assertThrows(
        IllegalStateException.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  enqueue(owner, id);
                  throw new IllegalStateException("business operation failed");
                }));
    assertEquals(0, outbox.count());
    transaction.executeWithoutResult(status -> enqueue(owner, id));
    assertEquals(1, outbox.count());
    when(kafka.send(anyString(), any(Object.class)))
        .thenThrow(new IllegalStateException("broker offline"));
    assertThrows(IllegalStateException.class, delivery::deliverBatch);
    assertEquals(1, outbox.count());
    doReturn(true).when(kafka).send(anyString(), any(Object.class));
    delivery.deliverBatch();
    assertEquals(0, outbox.count());
    verify(kafka, times(2))
        .send(
            eq("assessmentEvents-out-0"),
            argThat(
                value -> {
                  Message<?> message = (Message<?>) value;
                  return (owner + "/" + id).equals(message.getHeaders().get("eventKey"))
                      && message.getPayload().toString().contains("\"eventVersion\":2")
                      && message.getPayload().toString().contains(owner.toString());
                }));
  }

  @Test
  void snapshotMigrationQueuesOnceAndRollsBackItsMarkerOnFailure() {
    SnapshotSource source = mock(SnapshotSource.class);
    when(source.migrationKey()).thenReturn("test-v2-snapshot");
    UUID owner = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    doAnswer(
            invocation -> {
              enqueue(owner, id);
              throw new IllegalStateException("Snapshot generation failed");
            })
        .when(source)
        .enqueueSnapshots();
    assertThrows(IllegalStateException.class, () -> migration.runOnce(source));
    assertEquals(0, outbox.count());
    assertFalse(migrations.existsById(source.migrationKey()));
    doAnswer(
            invocation -> {
              enqueue(owner, id);
              return null;
            })
        .when(source)
        .enqueueSnapshots();
    migration.runOnce(source);
    migration.runOnce(source);
    assertEquals(1, outbox.count());
    assertTrue(migrations.existsById(source.migrationKey()));
    verify(source, times(2)).enqueueSnapshots();
  }

  @Test
  void publisherRequiresARealTransaction() {
    assertThrows(
        IllegalTransactionStateException.class,
        () -> enqueue(UUID.randomUUID(), UUID.randomUUID()));
  }
}
