package au.edu.uow.csci318.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {}

    public record ChatMessage(@NotBlank String role, @NotBlank @Size(max = 4000) String content) {}
    public record ChatRequest(@NotBlank @Size(max = 4000) String message,
                              @NotNull @Size(max = 20) List<@Valid ChatMessage> history) {}
    public record ChatResponse(String reply, String provider, String model, Instant answeredAt) {}
}
