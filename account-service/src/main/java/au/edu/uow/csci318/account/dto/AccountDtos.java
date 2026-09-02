package au.edu.uow.csci318.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record RegisterRequest(@NotBlank String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  String displayName) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ProfileUpdateRequest(@NotBlank String displayName, String institution, String course,
                                       String studyGoal, @NotBlank String timezone,
                                       @Size(max = 1_500_000) String profilePicture) {}
    public record ThemeUpdateRequest(@NotBlank String primaryColor, @NotBlank String accentColor,
                                     @NotBlank String backgroundColor, @NotBlank String surfaceColor,
                                     @NotBlank String textColor, String navigationColor) {}
    public record NavigationUpdateRequest(@NotEmpty @Size(max = 6) List<@NotBlank String> navigationOrder) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record AccountResponse(UUID id, String username, String displayName, String institution,
                                  String course, String studyGoal, String timezone, String primaryColor,
                                  String accentColor, String backgroundColor, String surfaceColor,
                                  String textColor, String navigationColor, String profilePicture,
                                  List<String> navigationOrder, Instant createdAt) {}
    public record SessionResponse(String token, Instant expiresAt, AccountResponse account) {}
    public record IdentityResponse(UUID accountId, String username) {}
}
