package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.application.CalendarReferences;
import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestCalendarReferences implements CalendarReferences {
  private final RestClient subjects;
  private final RestClient assessments;

  public RestCalendarReferences(
      RestClient.Builder builder,
      @Value("${services.subject-url}") String subjectUrl,
      @Value("${services.assessment-url}") String assessmentUrl) {
    subjects = builder.clone().baseUrl(subjectUrl).build();
    assessments = builder.clone().baseUrl(assessmentUrl).build();
  }

  public void verify(String authorization, UUID subjectId, UUID assessmentId) {
    if (assessmentId != null && subjectId == null)
      throw new IllegalArgumentException("Select the subject for the referenced assessment");
    try {
      if (subjectId != null)
        subjects
            .get()
            .uri("/api/subjects/{id}", subjectId)
            .header("Authorization", authorization)
            .retrieve()
            .toBodilessEntity();
      if (assessmentId != null) {
        AssessmentView assessment =
            assessments
                .get()
                .uri("/api/assessments/{id}", assessmentId)
                .header("Authorization", authorization)
                .retrieve()
                .body(AssessmentView.class);
        if (assessment == null || !subjectId.equals(assessment.subjectId()))
          throw new IllegalArgumentException("Assessment and subject do not match");
      }
    } catch (HttpClientErrorException.NotFound failure) {
      throw new IllegalArgumentException(
          "Referenced subject or assessment does not exist for this account");
    } catch (RestClientException failure) {
      throw new AcademicServiceException(
          "Academic records could not be verified. Try again when the services are available.",
          failure);
    }
  }

  public static class AcademicServiceException extends RuntimeException {
    public AcademicServiceException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
