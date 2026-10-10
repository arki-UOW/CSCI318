package au.edu.uow.csci318.activity.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.activity.domain.StudySession;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

class DescriptionPersistenceTest {
  @Test
  void supportsFullDescriptionAndUpgradesLegacyColumnWithoutDataLoss() throws Exception {
    String url = "jdbc:h2:mem:description-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    UUID original;
    try (var factory = configuration(url, "create").buildSessionFactory();
        var session = factory.openSession()) {
      session.beginTransaction();
      var entity = entity("Existing notes");
      original = entity.getId();
      session.persist(entity);
      session.getTransaction().commit();
    }
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      sql.execute("alter table study_sessions alter column description varchar(255)");
    }
    try (var factory = configuration(url, "update").buildSessionFactory();
        var session = factory.openSession()) {
      assertEquals("Existing notes", session.find(StudySession.class, original).getDescription());
      session.beginTransaction();
      var entity = entity("x".repeat(2000));
      session.persist(entity);
      session.getTransaction().commit();
      session.clear();
      assertEquals(
          2000, session.find(StudySession.class, entity.getId()).getDescription().length());
    }
    assertThrows(IllegalArgumentException.class, () -> entity("x".repeat(2001)));
  }

  private StudySession entity(String text) {
    UUID owner = UUID.randomUUID(), subject = UUID.randomUUID();
    return new StudySession(owner, subject, 30, LocalDate.now(), text);
  }

  private Configuration configuration(String url, String mode) {
    return new Configuration()
        .addAnnotatedClass(StudySession.class)
        .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
        .setProperty("hibernate.connection.url", url)
        .setProperty("hibernate.hbm2ddl.auto", mode)
        .setProperty(
            "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
  }
}
