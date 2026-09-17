package au.edu.uow.csci318.messaging.infrastructure;

import au.edu.uow.csci318.messaging.application.SnapshotSource;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan(basePackageClasses = MessagingConfiguration.class)
@EnableScheduling
public class MessagingConfiguration {
  @Bean
  org.springframework.context.ApplicationListener<ApplicationReadyEvent> migrateEvents(
      SnapshotMigration migration, List<SnapshotSource> sources) {
    return event -> sources.forEach(migration::runOnce);
  }
}
