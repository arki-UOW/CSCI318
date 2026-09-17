package au.edu.uow.csci318.planning;

import au.edu.uow.csci318.messaging.infrastructure.MessagingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@Import(MessagingConfiguration.class)
@EntityScan({"au.edu.uow.csci318.planning", "au.edu.uow.csci318.messaging.infrastructure"})
@EnableJpaRepositories({
  "au.edu.uow.csci318.planning",
  "au.edu.uow.csci318.messaging.infrastructure"
})
public class PlanningServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlanningServiceApplication.class, args);
  }

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
