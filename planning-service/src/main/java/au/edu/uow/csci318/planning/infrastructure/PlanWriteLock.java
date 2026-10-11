package au.edu.uow.csci318.planning.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Database row lock held until the caller's plan/calendar transaction commits. */
@Component
public class PlanWriteLock {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate independent;

  public PlanWriteLock(
      JdbcTemplate jdbc, PlatformTransactionManager manager, EntityManagerFactory schemaReady) {
    this.jdbc = jdbc;
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @PostConstruct
  public void initialise() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS planning_owner_locks (owner_id UUID PRIMARY KEY)");
    // Repair only owners affected by the old race, preserving plan IDs, content and calendar links.
    independent.executeWithoutResult(
        status -> {
          List<UUID> owners =
              jdbc.query(
                  "SELECT DISTINCT owner_id FROM study_plans GROUP BY owner_id, version HAVING"
                      + " COUNT(*) > 1",
                  (row, index) -> row.getObject(1, UUID.class));
          for (UUID owner : owners) {
            List<UUID> ids =
                jdbc.query(
                    "SELECT id FROM study_plans WHERE owner_id = ? ORDER BY created_at, id",
                    (row, index) -> row.getObject(1, UUID.class),
                    owner);
            for (int i = 0; i < ids.size(); i++)
              jdbc.update("UPDATE study_plans SET version = ? WHERE id = ?", -(i + 1), ids.get(i));
            for (int i = 0; i < ids.size(); i++)
              jdbc.update("UPDATE study_plans SET version = ? WHERE id = ?", i + 1, ids.get(i));
          }
        });
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_study_plan_owner_version ON study_plans(owner_id,"
            + " version)");
  }

  public void acquire(UUID owner) {
    // Bootstrap separately: competing first requests may both observe an absent row.
    try {
      independent.executeWithoutResult(
          status -> {
            if (jdbc.queryForList(
                    "SELECT owner_id FROM planning_owner_locks WHERE owner_id = ?", owner)
                .isEmpty())
              jdbc.update("INSERT INTO planning_owner_locks(owner_id) VALUES (?)", owner);
          });
    } catch (DuplicateKeyException alreadyCreated) {
      // The competing transaction committed the same owner row. Lock it below.
    }
    jdbc.queryForObject(
        "SELECT owner_id FROM planning_owner_locks WHERE owner_id = ? FOR UPDATE",
        UUID.class,
        owner);
  }
}
