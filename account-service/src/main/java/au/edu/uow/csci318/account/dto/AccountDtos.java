package au.edu.uow.csci318.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record RegisterRequest(@NotBlank String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  String displayName) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ProfileUpdateRequest(@NotBlank String displayName, String institution, String course,
                                       String studyGoal, @NotBlank String timezone) {}
    public record ThemeUpdateRequest(@NotBlank String primaryColor, @NotBlank String accentColor,
                                     @NotBlank String backgroundColor, @NotBlank String surfaceColor,
                                     @NotBlank String textColor) {}
    public record AccountResponse(UUID id, String username, String displayName, String institution,
                                  String course, String studyGoal, String timezone, String primaryColor,
                                  String accentColor, String backgroundColor, String surfaceColor,
                                  String textColor, Instant createdAt) {}
    public record SessionResponse(String token, Instant expiresAt, AccountResponse account) {}
    public record IdentityResponse(UUID accountId, String username) {}
}
