package au.edu.uow.csci318.subject.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class SubjectDtos {
 private SubjectDtos(){}
 public record AssessmentCandidate(@NotBlank String title,String type,@DecimalMin("0.0") @DecimalMax("100.0") Double weighting,LocalDate dueDate,@Min(1) Integer dueWeek,String description,@Positive Double estimatedHours,Double confidence,String warning){}
 public record ExtractionResult(@NotBlank String subjectCode,@NotBlank String subjectName,@Positive Integer creditPoints,@NotNull List<@Valid AssessmentCandidate> assessments,List<String> warnings){}
 public record ImportReview(UUID importId,String filename,String status,ExtractionResult extraction){}
 public record ConfirmImportRequest(@Valid @NotNull ExtractionResult extraction,@Min(0) @Max(10080) int weeklyStudyTargetMinutes){}
 public record SubjectResponse(UUID id,String code,String name,Integer creditPoints,int weeklyStudyTargetMinutes){}
}
