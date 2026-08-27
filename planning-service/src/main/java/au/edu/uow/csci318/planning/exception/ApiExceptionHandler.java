package au.edu.uow.csci318.planning.exception;

import au.edu.uow.csci318.planning.infrastructure.IdentityClient.IdentityServiceException;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient.UnauthorizedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorBody(Instant timestamp, int status, String error, String message, String path,
                     Map<String, String> validationErrors) {}

    @ExceptionHandler(UnauthorizedIdentityException.class)
    ResponseEntity<ErrorBody>unauthorized(RuntimeException e, HttpServletRequest r) {
        return make(HttpStatus.UNAUTHORIZED, e, r, null);
    }

    @ExceptionHandler(IdentityServiceException.class)
    ResponseEntity<ErrorBody>identityUnavailable(RuntimeException e, HttpServletRequest r) {
        return make(HttpStatus.SERVICE_UNAVAILABLE, e, r, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorBody>bad(RuntimeException e, HttpServletRequest r) {
        return make(HttpStatus.BAD_REQUEST, e, r, null);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody>missing(RuntimeException e, HttpServletRequest r) {
        return make(HttpStatus.NOT_FOUND, e, r, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody>invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> errors.put(x.getField(), x.getDefaultMessage()));
        return make(HttpStatus.BAD_REQUEST, new IllegalArgumentException("Validation failed"), r, errors);
    }

    private ResponseEntity<ErrorBody> make(HttpStatus status, RuntimeException e,
                                           HttpServletRequest request, Map<String, String> errors) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(),
                status.getReasonPhrase(), e.getMessage(), request.getRequestURI(), errors));
    }
}
