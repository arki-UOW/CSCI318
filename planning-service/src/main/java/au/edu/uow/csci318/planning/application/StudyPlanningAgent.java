package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.DeadlineScheduler;
import au.edu.uow.csci318.planning.domain.WeeklyAvailability;
import au.edu.uow.csci318.planning.domain.stream.AssessmentSnapshot;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Application orchestration only: fetch factual inputs, delegate domain policy, adapt output. */
@Component
public class StudyPlanningAgent {
  private final PlanningTools tools;
  private final StudyHistory history;
  private final DeadlineScheduler scheduler = new DeadlineScheduler();

  public StudyPlanningAgent(PlanningTools tools, StudyHistory history) {
    this.tools = tools;
    this.history = history;
  }

  public Schedule generate(UUID ownerId, PlanRequest request, String authorization) {
    List<AssessmentSnapshot> assessments =
        tools.getIncompleteAssessments(authorization).stream()
            .map(
                item ->
                    new AssessmentSnapshot(
                        item.id(),
                        item.subjectId(),
                        item.title(),
                        item.type(),
                        item.weighting(),
                        item.dueDate(),
                        item.dueWeek(),
                        item.description(),
                        item.estimatedMinutes(),
                        item.priority(),
                        item.status(),
                        item.updatedAt()))
            .toList();
    var completed = history.completedAssessmentMinutes(ownerId);
    boolean overdueWork = assessments.stream().anyMatch(item -> item.dueDate() != null
        && item.dueDate().isBefore(request.startDate())
        && (item.estimatedMinutes() == null ? 120 : item.estimatedMinutes()) > completed.getOrDefault(item.id(), 0));
    DeadlineScheduler.Schedule result =
        scheduler.schedule(
            request.startDate(),
            WeeklyAvailability.from(request.startDate(), request.dailyAvailabilityMinutes()),
            assessments,
            completed);
    if (result.scheduledMinutes() == 0 && (result.requestedMinutes() > 0 || overdueWork)) {
      throw new IllegalArgumentException(result.requestedMinutes() > 0
          ? "No study time fits before your assessment deadlines. Add earlier availability or review the deadlines. Your current plan is unchanged."
          : "Your unfinished work is overdue for this planning period. Review its deadlines or mark completed work as done. Your current plan is unchanged.");
    }
    List<PlanItem> items =
        result.items().stream()
            .map(
                item ->
                    new PlanItem(
                        item.date(),
                        item.subjectId(),
                        item.assessmentId(),
                        item.title(),
                        item.allocatedMinutes(),
                        item.repetitionStage()))
            .toList();
    return new Schedule(
        items, result.endDate(), result.requestedMinutes(), result.scheduledMinutes());
  }

  public java.util.Map<UUID, Integer> completedMinutes(UUID ownerId) {
    return history.completedAssessmentMinutes(ownerId);
  }

  public record Schedule(
      List<PlanItem> items, LocalDate endDate, int requestedMinutes, int scheduledMinutes) {
    public int unscheduledMinutes() {
      return Math.max(0, requestedMinutes - scheduledMinutes);
    }
  }
}
