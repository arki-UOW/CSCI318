package au.edu.uow.csci318.activity.exception;

import au.edu.uow.csci318.activity.application.StudyActivityApplicationService.DependencyException;
import au.edu.uow.csci318.activity.infrastructure.IdentityClient.IdentityServiceException;
import au.edu.uow.csci318.activity.infrastructure.IdentityClient.UnauthorizedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorBody(Instant timestamp, int status, String error, String message, String path,
                     Map<String, String> validationErrors) {}
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorBody> bad(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.BAD_REQUEST, exception, request); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> missing(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.NOT_FOUND, exception, request); }
    @ExceptionHandler({DependencyException.class, IdentityServiceException.class})
    ResponseEntity<ErrorBody> unavailable(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.SERVICE_UNAVAILABLE, exception, request); }
    @ExceptionHandler(UnauthorizedIdentityException.class)
    ResponseEntity<ErrorBody> unauthorized(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.UNAUTHORIZED, exception, request); }
    private ResponseEntity<ErrorBody> make(HttpStatus status, RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI(), null));
    }
}
