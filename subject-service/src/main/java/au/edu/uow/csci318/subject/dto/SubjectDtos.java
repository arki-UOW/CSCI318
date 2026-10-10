package au.edu.uow.csci318.subject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class SubjectDtos {
  private SubjectDtos() {}

  public record StudyTargetRequest(@NotNull @Min(0) @Max(10080) Integer minutes) {}

  public record AssessmentCandidate(
      @NotBlank @Size(max = 255) String title,
      @Size(max = 255) String type,
      @DecimalMin("0.0") @DecimalMax("100.0") Double weighting,
      LocalDate dueDate,
      @Min(1) @Max(52) Integer dueWeek,
      @Size(max = 2000) String description,
      @Positive Double estimatedHours,
      Double confidence,
      String warning) {}

  public record ExtractionResult(
      @NotBlank @Size(max = 255) String subjectCode,
      @NotBlank @Size(max = 255) String subjectName,
      @Positive Integer creditPoints,
      @NotNull List<@Valid AssessmentCandidate> assessments,
      List<String> warnings) {}

  public record ImportReview(
      UUID importId, String filename, String status, ExtractionResult extraction) {}

  public record ConfirmImportRequest(
      @Valid @NotNull ExtractionResult extraction,
      @Min(0) @Max(10080) int weeklyStudyTargetMinutes) {}

  public record ManualSubjectRequest(
      @NotBlank @Size(max = 255) String code,
      @NotBlank @Size(max = 255) String name,
      @Positive Integer creditPoints,
      @Min(0) @Max(10080) int weeklyStudyTargetMinutes,
      @NotNull List<@Valid AssessmentCandidate> assessments) {}

  public record UpdateSubjectRequest(
      @NotBlank @Size(max = 255) String code,
      @NotBlank @Size(max = 255) String name,
      @Positive Integer creditPoints,
      @Min(0) @Max(10080) int weeklyStudyTargetMinutes) {}

  public record SubjectResponse(
      UUID id, String code, String name, Integer creditPoints, int weeklyStudyTargetMinutes) {}

  public record AiStatus(String provider, String model, boolean configured, String message) {}
}
