package au.edu.uow.csci318.subject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class SubjectServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SubjectServiceApplication.class, args); }
    @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}
