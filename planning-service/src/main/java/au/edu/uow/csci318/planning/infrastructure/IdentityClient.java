package au.edu.uow.csci318.planning.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

@Component
public class IdentityClient {
    private final RestClient accounts;
    public IdentityClient(RestClient.Builder builder, @Value("${services.account-url}") String url) { accounts = builder.baseUrl(url).build(); }
    public UUID require(String authorization) {
        if (authorization == null || authorization.isBlank()) throw new UnauthorizedIdentityException("Sign in to continue");
        try {
            Identity identity = accounts.get().uri("/api/auth/validate").header("Authorization", authorization).retrieve().body(Identity.class);
            if (identity == null) throw new UnauthorizedIdentityException("Sign in to continue");
            return identity.accountId();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new UnauthorizedIdentityException("Your session is invalid. Sign in again.");
            throw new IdentityServiceException("Account Service is unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw new IdentityServiceException("Account Service is unavailable", exception);
        }
    }
    record Identity(UUID accountId, String username) {}
    public static class UnauthorizedIdentityException extends RuntimeException { public UnauthorizedIdentityException(String message) { super(message); } }
    public static class IdentityServiceException extends RuntimeException { public IdentityServiceException(String message, Throwable cause) { super(message, cause); } }
}
