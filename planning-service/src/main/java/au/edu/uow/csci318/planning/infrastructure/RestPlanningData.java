package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.application.PlanningTools;
import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import au.edu.uow.csci318.planning.dto.PlanningDtos.SubjectView;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestPlanningData implements PlanningTools {
  private final RestClient assessments;
  private final RestClient subjects;
  private final RestClient activity;

  public RestPlanningData(
      RestClient.Builder builder,
      @Value("${services.assessment-url}") String assessmentUrl,
      @Value("${services.subject-url}") String subjectUrl,
      @Value("${services.activity-url}") String activityUrl) {
    assessments = builder.clone().baseUrl(assessmentUrl).build();
    subjects = builder.clone().baseUrl(subjectUrl).build();
    activity = builder.clone().baseUrl(activityUrl).build();
  }

  public List<AssessmentView> getIncompleteAssessments(String authorization) {
    return getAssessments(authorization).stream()
        .filter(assessment -> "INCOMPLETE".equals(assessment.status()))
        .filter(this::plausibleAssessment)
        .toList();
  }

  public List<AssessmentView> getUpcomingAssessments(String authorization) {
    return getIncompleteAssessments(authorization).stream()
        .filter(
            assessment ->
                assessment.dueDate() != null && !assessment.dueDate().isBefore(LocalDate.now()))
        .sorted(Comparator.comparing(AssessmentView::dueDate))
        .toList();
  }

  public List<AssessmentView> getAssessmentsDueThisWeek(String authorization) {
    LocalDate from = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    LocalDate to = from.plusDays(6);
    return getIncompleteAssessments(authorization).stream()
        .filter(
            assessment ->
                assessment.dueDate() != null
                    && !assessment.dueDate().isBefore(from)
                    && !assessment.dueDate().isAfter(to))
        .toList();
  }

  public List<SubjectView> getSubjects(String authorization) {
    List<SubjectView> response =
        subjects
            .get()
            .uri("/api/subjects")
            .header("Authorization", authorization)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return response == null ? List.of() : response;
  }

  public int getStudiedMinutes(String authorization, UUID subjectId, LocalDate weekStart) {
    StudySummary response =
        activity
            .get()
            .uri(
                uri ->
                    uri.path("/api/study-sessions/summary")
                        .queryParam("subjectId", subjectId)
                        .queryParam("weekOf", weekStart)
                        .build())
            .header("Authorization", authorization)
            .retrieve()
            .body(StudySummary.class);
    return response == null ? 0 : response.totalMinutes();
  }

  private List<AssessmentView> getAssessments(String authorization) {
    List<AssessmentView> response =
        assessments
            .get()
            .uri("/api/assessments")
            .header("Authorization", authorization)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return response == null ? List.of() : response;
  }

  private boolean plausibleAssessment(AssessmentView assessment) {
    String title = assessment.title() == null ? "" : assessment.title().toLowerCase();
    return title.length() >= 3
        && title.length() <= 120
        && !title.matches(
            ".*(learning outcome|eligible for a pass|submitted late|late submission|"
                + "academic integrity|marking criteria|name type|student must|policy).*?");
  }

  private record StudySummary(
      UUID subjectId, LocalDate weekStart, LocalDate weekEnd, int totalMinutes, int sessionCount) {}
}
