package au.edu.uow.csci318.account.application;

import au.edu.uow.csci318.account.domain.Account;
import au.edu.uow.csci318.account.domain.AccountSession;
import au.edu.uow.csci318.account.dto.AccountDtos.*;
import au.edu.uow.csci318.account.infrastructure.AccountRepository;
import au.edu.uow.csci318.account.infrastructure.AccountSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AccountApplicationService {
    private static final Set<String> NAVIGATION_VIEWS = Set.of(
            "dashboard", "subjects", "assessments", "plan", "calendar", "activity");
    private final AccountRepository accounts;
    private final AccountSessionRepository sessions;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(10);
    private final SecureRandom random = new SecureRandom();
    private final int sessionDays;

    public AccountApplicationService(AccountRepository accounts, AccountSessionRepository sessions,
                                     @Value("${study.auth.session-days:30}") int sessionDays) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.sessionDays = sessionDays;
    }

    @Transactional
    public SessionResponse register(RegisterRequest request) {
        String username = Account.normaliseUsername(request.username());
        if (accounts.existsByUsername(username)) throw new DuplicateUsernameException("That username is already in use");
        validatePassword(request.password());
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim();
        Account account = accounts.save(new Account(username, passwords.encode(request.password()), displayName,
                ZoneId.systemDefault().getId()));
        return createSession(account);
    }

    @Transactional
    public SessionResponse login(LoginRequest request) {
        Account account = accounts.findByUsername(Account.normaliseUsername(request.username()))
                .orElseThrow(() -> new UnauthorizedException("Incorrect username or password"));
        if (!passwords.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("Incorrect username or password");
        }
        return createSession(account);
    }

    @Transactional
    public AccountResponse session(String authorization) {
        return response(requireAccount(authorization));
    }

    @Transactional
    public IdentityResponse validate(String authorization) {
        Account account = requireAccount(authorization);
        return new IdentityResponse(account.getId(), account.getUsername());
    }

    @Transactional
    public void logout(String authorization) {
        String token = bearer(authorization);
        sessions.findByTokenHash(hash(token)).ifPresent(sessions::delete);
    }

    @Transactional
    public AccountResponse updateProfile(String authorization, ProfileUpdateRequest request) {
        ZoneId.of(request.timezone());
        Account account = requireAccount(authorization);
        account.updateProfile(request.displayName(), request.institution(), request.course(), request.studyGoal(), request.timezone());
        if (request.profilePicture() != null) account.updateProfilePicture(request.profilePicture());
        return response(accounts.save(account));
    }

    @Transactional
    public AccountResponse updateTheme(String authorization, ThemeUpdateRequest request) {
        Account account = requireAccount(authorization);
        account.updateTheme(request.primaryColor(), request.accentColor(), request.backgroundColor(),
                request.surfaceColor(), request.textColor(), request.navigationColor());
        return response(accounts.save(account));
    }

    @Transactional
    public AccountResponse updateNavigation(String authorization, NavigationUpdateRequest request) {
        LinkedHashSet<String> order = new LinkedHashSet<>(request.navigationOrder());
        if (order.size() != NAVIGATION_VIEWS.size() || !order.equals(NAVIGATION_VIEWS)) {
            throw new IllegalArgumentException("Navigation order must contain every visible tab exactly once");
        }
        Account account = requireAccount(authorization);
        account.updateNavigationOrder(String.join(",", order));
        return response(accounts.save(account));
    }

    @Transactional
    public void changePassword(String authorization, PasswordChangeRequest request) {
        validatePassword(request.newPassword());
        String currentTokenHash = hash(bearer(authorization));
        Account account = requireAccount(authorization);
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        account.changePasswordHash(passwords.encode(request.newPassword()));
        accounts.save(account);
        sessions.deleteByAccountIdAndTokenHashNot(account.getId(), currentTokenHash);
    }

    private SessionResponse createSession(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plus(Duration.ofDays(sessionDays));
        sessions.save(new AccountSession(account.getId(), hash(token), expires));
        return new SessionResponse(token, expires, response(account));
    }

    private Account requireAccount(String authorization) {
        String token = bearer(authorization);
        AccountSession session = sessions.findByTokenHash(hash(token))
                .orElseThrow(() -> new UnauthorizedException("Your session is invalid. Sign in again."));
        if (session.expired(Instant.now())) {
            sessions.delete(session);
            throw new UnauthorizedException("Your session expired. Sign in again.");
        }
        session.touch();
        sessions.save(session);
        return accounts.findById(session.getAccountId())
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() < 10) {
            throw new UnauthorizedException("Sign in to continue");
        }
        return authorization.substring(7).trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must be between 8 and 72 characters");
        }
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not secure the session token", exception);
        }
    }

    private AccountResponse response(Account account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                account.getInstitution(), account.getCourse(), account.getStudyGoal(), account.getTimezone(),
                account.getPrimaryColor(), account.getAccentColor(), account.getBackgroundColor(),
                account.getSurfaceColor(), account.getTextColor(), account.getNavigationColor(),
                account.getProfilePicture(), List.of(account.getNavigationOrder().split(",")), account.getCreatedAt());
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }
    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String message) { super(message); }
    }
}
