package au.edu.uow.csci318.messaging.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_migrations")
public class EventMigration {
  @Id private String id;

  protected EventMigration() {}

  EventMigration(String id) {
    this.id = id;
  }
}
