package au.edu.uow.csci318.planning.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PlanWriteLockTest {
  @Test
  void repairsLegacyDuplicatesWithoutChangingIdsAndEnforcesUniqueVersions() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(ds);
    jdbc.execute(
        "CREATE TABLE study_plans(id UUID PRIMARY KEY, owner_id UUID, version INT, created_at"
            + " TIMESTAMP)");
    UUID owner = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    jdbc.update("INSERT INTO study_plans VALUES(?, ?, 1, '2026-10-10 10:00:00')", first, owner);
    jdbc.update("INSERT INTO study_plans VALUES(?, ?, 1, '2026-10-10 11:00:00')", second, owner);
    var lock =
        new PlanWriteLock(
            jdbc, new DataSourceTransactionManager(ds), mock(EntityManagerFactory.class));
    lock.initialise();
    assertEquals(
        1,
        jdbc.queryForObject("SELECT version FROM study_plans WHERE id = ?", Integer.class, first));
    assertEquals(
        2,
        jdbc.queryForObject("SELECT version FROM study_plans WHERE id = ?", Integer.class, second));
    assertThrows(
        org.springframework.dao.DuplicateKeyException.class,
        () ->
            jdbc.update(
                "INSERT INTO study_plans VALUES(?, ?, 2, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                owner));
    lock.initialise(); // restart is idempotent
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM study_plans", Integer.class));
    jdbc.execute("SHUTDOWN");
  }
}
