package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.dto.SubjectDtos.AssessmentCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

@Component
class AssessmentImportClient {
    private final RestClient assessments;
    private final ObjectMapper json;

    AssessmentImportClient(
            RestClient.Builder builder,
            ObjectMapper json,
            @Value("${services.assessment-url}") String assessmentServiceUrl) {
        this.assessments = builder.baseUrl(assessmentServiceUrl).build();
        this.json = json;
    }

    void importAssessments(String authorization, UUID subjectId, List<AssessmentCandidate> candidates) {
        try {
            assessments.post()
                    .uri("/api/assessments/import")
                    .header("Authorization", authorization)
                    .body(new AssessmentImport(subjectId, candidates))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            String detail = responseMessage(exception);
            if (exception.getStatusCode().is4xxClientError()) {
                throw new AssessmentImportRejectedException(
                        "The assessments need another review: " + detail, exception);
            }
            throw new SubjectApplicationService.ServiceDependencyException(
                    "Assessment Service is unavailable. Confirmation is still pending; try again when it is running.",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new SubjectApplicationService.ServiceDependencyException(
                    "Assessment Service could not be reached. Confirmation is still pending; try again when it is running.",
                    exception);
        }
    }

    private String responseMessage(RestClientResponseException exception) {
        try {
            String message = json.readTree(exception.getResponseBodyAsString()).path("message").asText();
            return message.isBlank() ? "Assessment Service rejected the submitted values" : message;
        } catch (Exception ignored) {
            return "Assessment Service rejected the submitted values";
        }
    }

    record AssessmentImport(UUID subjectId, List<AssessmentCandidate> assessments) {
    }

    static class AssessmentImportRejectedException extends IllegalArgumentException {
        AssessmentImportRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
