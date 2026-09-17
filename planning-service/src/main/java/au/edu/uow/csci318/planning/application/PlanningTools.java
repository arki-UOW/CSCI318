package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import au.edu.uow.csci318.planning.dto.PlanningDtos.SubjectView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application data-access port; synchronous reads are reserved for planning/assistant use cases.
 */
public interface PlanningTools {
  List<AssessmentView> getIncompleteAssessments(String authorization);

  List<AssessmentView> getUpcomingAssessments(String authorization);

  List<AssessmentView> getAssessmentsDueThisWeek(String authorization);

  List<SubjectView> getSubjects(String authorization);

  int getStudiedMinutes(String authorization, UUID subjectId, LocalDate weekStart);
}
