package au.edu.uow.csci318.planning.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

class StudyPlanSchemaUpgradeTest {
  @Test
  void hibernateUpdateWidensLegacyExplanationWithoutLosingExistingPlans() throws Exception {
    String url = "jdbc:h2:mem:upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    try (var connection = DriverManager.getConnection(url);
        var sql = connection.createStatement()) {
      sql.execute(
          "create table study_plans (id uuid primary key, owner_id uuid not null, start_date date"
              + " not null, end_date date not null, version integer not null, items_json clob not"
              + " null, explanation varchar(255) not null, created_at timestamp(6) with time zone"
              + " not null)");
      sql.execute(
          "insert into study_plans values ('"
              + id
              + "','"
              + owner
              + "',date '2026-10-12',date '2026-10-18',1,'[]','Existing plan',current_timestamp)");
    }
    var configuration =
        new Configuration()
            .addAnnotatedClass(StudyPlan.class)
            .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
            .setProperty("hibernate.connection.url", url)
            .setProperty("hibernate.hbm2ddl.auto", "update")
            .setProperty(
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
    try (var factory = configuration.buildSessionFactory();
        var session = factory.openSession()) {
      assertEquals("Existing plan", session.find(StudyPlan.class, id).getExplanation());
      session.beginTransaction();
      StudyPlan next =
          new StudyPlan(
              owner,
              LocalDate.of(2026, 10, 12),
              LocalDate.of(2026, 10, 18),
              2,
              "[]",
              "x".repeat(2000));
      session.persist(next);
      session.getTransaction().commit();
      session.clear();
      assertEquals(2000, session.find(StudyPlan.class, next.getId()).getExplanation().length());
    }
  }
}
