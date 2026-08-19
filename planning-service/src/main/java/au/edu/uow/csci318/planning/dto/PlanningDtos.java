package au.edu.uow.csci318.planning.dto;import jakarta.validation.constraints.*;import java.time.*;import java.util.*;
public final class PlanningDtos{private PlanningDtos(){}
 public record AssessmentView(UUID id,UUID subjectId,String title,String type,Double weighting,LocalDate dueDate,Integer dueWeek,String description,Integer estimatedMinutes,String priority,String status,Instant updatedAt){}
 public record SubjectView(UUID id,String code,String name,Integer creditPoints,int weeklyStudyTargetMinutes){}
 public record PlanItem(LocalDate date,UUID subjectId,UUID assessmentId,String title,int allocatedMinutes){}
 public record PlanRequest(@NotNull LocalDate startDate,@NotNull Map<LocalDate,@Positive Integer>dailyAvailabilityMinutes){}
 public record PlanResponse(UUID id,LocalDate startDate,LocalDate endDate,int version,List<PlanItem>items,String explanation,Instant createdAt){}
 public record WorkloadSummary(int incompleteAssessments,int dueThisWeek,int dueWithinSevenDays,int estimatedMinutes,int highPriorityCount,String status){}
 public record StudyProgress(UUID subjectId,int studiedMinutes,int targetMinutes,int remainingMinutes,String state){}
 public record ThisWeek(LocalDate weekStart,LocalDate weekEnd,List<AssessmentView>dueThisWeek,List<AssessmentView>upcoming,WorkloadSummary workload,List<StudyProgress>studyProgress,List<PlanItem>planItems){}
}
