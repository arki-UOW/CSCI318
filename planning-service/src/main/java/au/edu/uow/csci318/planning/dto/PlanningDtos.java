package au.edu.uow.csci318.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlanningDtos {
    private PlanningDtos() {
    }

    public record AssessmentView(UUID id, UUID subjectId, String title, String type, Double weighting,
                                 LocalDate dueDate, Integer dueWeek, String description, Integer estimatedMinutes,
                                 String priority, String status, Instant updatedAt) {
    }

    public record SubjectView(UUID id, String code, String name, Integer creditPoints,
                              int weeklyStudyTargetMinutes) {
    }

    public record PlanItem(LocalDate date, UUID subjectId, UUID assessmentId, String title,
                           int allocatedMinutes, int repetitionStage) {
        public PlanItem(LocalDate date, UUID subjectId, UUID assessmentId, String title,
                        int allocatedMinutes) {
            this(date, subjectId, assessmentId, title, allocatedMinutes, 0);
        }
    }

    public record PlanRequest(@NotNull LocalDate startDate,
                              @NotNull Map<LocalDate, @PositiveOrZero @Max(1440) Integer> dailyAvailabilityMinutes) {
    }

    public record PlanResponse(UUID id, LocalDate startDate, LocalDate endDate, int version,
                               List<PlanItem> items, String explanation, Instant createdAt) {
    }

    public record TimeSlot(@NotNull LocalTime start, @NotNull LocalTime end) {
    }

    public record AvailabilityChatRequest(@NotBlank String message, @NotNull LocalDate weekStart,
                                          @NotNull Map<LocalDate, List<@Valid TimeSlot>> availability) {
    }

    public record AvailabilityChatResponse(String reply, Map<LocalDate, List<TimeSlot>> availability,
                                           Map<LocalDate, Integer> dailyAvailabilityMinutes,
                                           boolean readyToPlan, String provider) {
    }

    public record AiStatus(String provider, String model, boolean configured, String message) {
    }

    public record WorkloadSummary(int incompleteAssessments, int dueThisWeek, int dueWithinSevenDays,
                                  int estimatedMinutes, int highPriorityCount, String status) {
    }

    public record StudyProgress(UUID subjectId, int studiedMinutes, int targetMinutes,
                                int remainingMinutes, String state) {
    }

    public record ThisWeek(LocalDate weekStart, LocalDate weekEnd, List<AssessmentView> dueThisWeek,
                           List<AssessmentView> upcoming, WorkloadSummary workload,
                           List<StudyProgress> studyProgress, List<PlanItem> planItems) {
    }
}
