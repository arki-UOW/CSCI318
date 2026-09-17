package au.edu.uow.csci318.messaging.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMigrationRepository extends JpaRepository<EventMigration, String> {}
