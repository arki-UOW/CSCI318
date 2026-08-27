package au.edu.uow.csci318.planning.dto;

import au.edu.uow.csci318.planning.domain.CalendarEntry.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record SaveRequest(@NotBlank @Size(max = 160) String title,
                              @Size(max = 2000) String description,
                              @NotNull EntryType type,
                              UUID subjectId,
                              UUID assessmentId,
                              @NotNull LocalDateTime startAt,
                              @NotNull LocalDateTime endAt,
                              boolean spacedRepetition) {}

    public record EntryResponse(UUID id, UUID subjectId, UUID assessmentId, UUID planId,
                                UUID sourceEntryId, String title, String description,
                                EntryType type, LocalDateTime startAt, LocalDateTime endAt,
                                EntryStatus status, Origin origin, boolean spacedRepetition,
                                int repetitionStage, Instant createdAt, Instant updatedAt,
                                Instant completedAt) {}

    public record CompletionResponse(EntryResponse completed, EntryResponse nextReview) {}
}
