package au.edu.uow.csci318.assessment;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;import org.springframework.context.annotation.Bean;import org.springframework.web.client.RestClient;
@SpringBootApplication public class AssessmentServiceApplication{public static void main(String[]a){SpringApplication.run(AssessmentServiceApplication.class,a);}@Bean RestClient.Builder restClientBuilder(){return RestClient.builder();}}
