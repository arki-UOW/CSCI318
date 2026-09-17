package au.edu.uow.csci318.planning.infrastructure.stream;

import au.edu.uow.csci318.planning.application.ProjectionIngestion;
import java.util.function.*;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.*;

@Configuration
public class StreamBindings {
  @Bean
  Function<KStream<String, String>, KStream<String, String>> workloadStream(
      PlanningStreamTopology topology) {
    return topology::workload;
  }

  @Bean
  BiFunction<KStream<String, String>, KStream<String, String>, KStream<String, String>>
      studyProgressStream(PlanningStreamTopology topology) {
    return topology::progress;
  }

  @Bean
  Consumer<String> workloadProjection(ProjectionIngestion ingestion) {
    return ingestion::workload;
  }

  @Bean
  Consumer<String> progressProjection(ProjectionIngestion ingestion) {
    return ingestion::progress;
  }

  @Bean
  java.time.Clock clock() {
    return java.time.Clock.systemUTC();
  }
}
