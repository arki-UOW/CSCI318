package au.edu.uow.csci318.messaging.infrastructure;

import au.edu.uow.csci318.messaging.application.SnapshotSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotMigration {
  private final EventMigrationRepository migrations;

  public SnapshotMigration(EventMigrationRepository migrations) {
    this.migrations = migrations;
  }

  @Transactional
  public void runOnce(SnapshotSource source) {
    if (migrations.existsById(source.migrationKey())) return;
    source.enqueueSnapshots();
    migrations.save(new EventMigration(source.migrationKey()));
  }
}
