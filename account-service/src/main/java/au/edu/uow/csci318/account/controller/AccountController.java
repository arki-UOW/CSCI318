package au.edu.uow.csci318.account.controller;

import au.edu.uow.csci318.account.application.AccountApplicationService;
import au.edu.uow.csci318.account.dto.AccountDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AccountController {
    private final AccountApplicationService service;

    public AccountController(AccountApplicationService service) { this.service = service; }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@Valid @RequestBody RegisterRequest request) { return service.register(request); }

    @PostMapping("/auth/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @GetMapping("/auth/session")
    public AccountResponse session(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.session(authorization);
    }

    @GetMapping("/auth/validate")
    public IdentityResponse validate(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.validate(authorization);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(authorization);
    }

    @PatchMapping("/profile")
    public AccountResponse profile(@RequestHeader("Authorization") String authorization,
                                   @Valid @RequestBody ProfileUpdateRequest request) {
        return service.updateProfile(authorization, request);
    }

    @PatchMapping("/settings/theme")
    public AccountResponse theme(@RequestHeader("Authorization") String authorization,
                                 @Valid @RequestBody ThemeUpdateRequest request) {
        return service.updateTheme(authorization, request);
    }

    @PatchMapping("/settings/navigation")
    public AccountResponse navigation(@RequestHeader("Authorization") String authorization,
                                      @Valid @RequestBody NavigationUpdateRequest request) {
        return service.updateNavigation(authorization, request);
    }

    @PatchMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestHeader("Authorization") String authorization,
                         @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(authorization, request);
    }
}
