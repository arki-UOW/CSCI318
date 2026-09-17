package au.edu.uow.csci318.planning.infrastructure.projection;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.planning.application.ProjectionStore;
import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = JpaProjectionStoreTest.Config.class)
class JpaProjectionStoreTest {
  @Configuration
  @EntityScan(basePackageClasses = ProjectionDocument.class)
  @EnableJpaRepositories(basePackageClasses = ProjectionDocumentRepository.class)
  @Import(JpaProjectionStore.class)
  static class Config {
    @Bean
    ObjectMapper json() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }

  @Autowired ProjectionStore store;

  @Test
  void persistsQueryableStateAndRejectsDuplicateOrStaleSinkMessages() {
    UUID owner = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    AssessmentSnapshot item =
        new AssessmentSnapshot(
            id,
            subject,
            "Report",
            "Report",
            30.0,
            LocalDate.of(2026, 9, 30),
            null,
            null,
            120,
            "HIGH",
            "INCOMPLETE",
            Instant.EPOCH);
    WorkloadState first =
        WorkloadState.empty().apply(new AssessmentChange(owner, 1, false, item, Instant.EPOCH));
    assertTrue(store.saveWorkload(first));
    assertFalse(store.saveWorkload(first));
    WorkloadState deleted = first.apply(new AssessmentChange(owner, 2, true, item, Instant.EPOCH));
    assertTrue(store.saveWorkload(deleted));
    assertFalse(store.saveWorkload(first));
    assertEquals(0, store.workload(owner).orElseThrow().incompleteCount());
    assertTrue(store.workload(UUID.randomUUID()).isEmpty());
    ProgressState progress =
        ProgressState.empty()
            .apply(
                new ProgressChange(
                    owner,
                    subject,
                    null,
                    1,
                    false,
                    null,
                    new SubjectSnapshot(subject, "CSCI318", "Software Engineering", 240),
                    Instant.EPOCH));
    assertTrue(store.saveProgress(progress));
    assertEquals(240, store.progress(owner).getFirst().subject().weeklyStudyTargetMinutes());
    assertTrue(store.progress(UUID.randomUUID()).isEmpty());
  }
}
