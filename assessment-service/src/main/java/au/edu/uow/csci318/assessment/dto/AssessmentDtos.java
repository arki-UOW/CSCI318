package au.edu.uow.csci318.assessment.dto;

import au.edu.uow.csci318.assessment.domain.Assessment.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class AssessmentDtos {
  private AssessmentDtos() {}

  public record AssessmentCandidate(
      @NotBlank @Size(max = 255) String title,
      @Size(max = 255) String type,
      @DecimalMin("0") @DecimalMax("100") Double weighting,
      LocalDate dueDate,
      @Min(1) @Max(52) Integer dueWeek,
      @Size(max = 2000) String description,
      @Positive Double estimatedHours,
      Double confidence,
      String warning) {}

  public record ImportRequest(
      @NotNull UUID subjectId, @NotEmpty List<@Valid AssessmentCandidate> assessments) {}

  public record CreateRequest(
      @NotNull UUID subjectId,
      @NotBlank @Size(max = 255) String title,
      @Size(max = 255) String type,
      @DecimalMin("0") @DecimalMax("100") Double weighting,
      LocalDate dueDate,
      @Min(1) @Max(52) Integer dueWeek,
      @Size(max = 2000) String description,
      @PositiveOrZero Integer estimatedMinutes,
      Priority priority) {}

  public record UpdateRequest(
      @NotBlank @Size(max = 255) String title,
      @Size(max = 255) String type,
      @DecimalMin("0") @DecimalMax("100") Double weighting,
      LocalDate dueDate,
      @Min(1) @Max(52) Integer dueWeek,
      @Size(max = 2000) String description,
      @PositiveOrZero Integer estimatedMinutes,
      Priority priority) {}

  public record Response(
      UUID id,
      UUID subjectId,
      String title,
      String type,
      Double weighting,
      LocalDate dueDate,
      Integer dueWeek,
      @Size(max = 2000) String description,
      Integer estimatedMinutes,
      Priority priority,
      Status status,
      Instant updatedAt) {}
}
