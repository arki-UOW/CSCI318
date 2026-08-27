package au.edu.uow.csci318.assessment.exception;

import au.edu.uow.csci318.assessment.application.AssessmentApplicationService.DependencyException;
import au.edu.uow.csci318.assessment.infrastructure.IdentityClient.IdentityServiceException;
import au.edu.uow.csci318.assessment.infrastructure.IdentityClient.UnauthorizedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorBody(Instant timestamp, int status, String error, String message, String path,
                     Map<String, String> validationErrors) {}
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorBody> bad(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.BAD_REQUEST, exception, request, null); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> missing(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.NOT_FOUND, exception, request, null); }
    @ExceptionHandler({DependencyException.class, IdentityServiceException.class})
    ResponseEntity<ErrorBody> unavailable(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.SERVICE_UNAVAILABLE, exception, request, null); }
    @ExceptionHandler(UnauthorizedIdentityException.class)
    ResponseEntity<ErrorBody> unauthorized(RuntimeException exception, HttpServletRequest request) { return make(HttpStatus.UNAUTHORIZED, exception, request, null); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(item -> fields.put(item.getField(), item.getDefaultMessage()));
        return make(HttpStatus.BAD_REQUEST, new IllegalArgumentException("Validation failed"), request, fields);
    }
    private ResponseEntity<ErrorBody> make(HttpStatus status, RuntimeException exception,
                                           HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI(), fields));
    }
}
