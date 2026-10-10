package au.edu.uow.csci318.subject.exception;

import au.edu.uow.csci318.subject.application.SubjectApplicationService.ServiceDependencyException;
import au.edu.uow.csci318.subject.infrastructure.IdentityClient.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  public record ErrorBody(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path,
      Map<String, String> validationErrors) {}

  @ExceptionHandler({UnauthorizedIdentityException.class})
  ResponseEntity<Object> unauthorized(RuntimeException e, HttpServletRequest r) {
    return response(HttpStatus.UNAUTHORIZED, e.getMessage(), r.getRequestURI(), Map.of());
  }

  @ExceptionHandler({IdentityServiceException.class, ServiceDependencyException.class})
  ResponseEntity<Object> unavailable(RuntimeException e, HttpServletRequest r) {
    return response(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), r.getRequestURI(), Map.of());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    IllegalStateException.class,
    java.time.DateTimeException.class
  })
  ResponseEntity<Object> bad(RuntimeException e, HttpServletRequest r) {
    return response(HttpStatus.BAD_REQUEST, e.getMessage(), r.getRequestURI(), Map.of());
  }

  @ExceptionHandler({NoSuchElementException.class})
  ResponseEntity<Object> missing(RuntimeException e, HttpServletRequest r) {
    return response(HttpStatus.NOT_FOUND, e.getMessage(), r.getRequestURI(), Map.of());
  }

  @ExceptionHandler({
    org.springframework.dao.DataIntegrityViolationException.class,
    org.springframework.dao.OptimisticLockingFailureException.class
  })
  ResponseEntity<Object> conflict(RuntimeException e, HttpServletRequest r) {
    return response(
        HttpStatus.CONFLICT,
        "The record conflicts with an existing or concurrently changed record. Refresh and retry.",
        r.getRequestURI(),
        Map.of());
  }

  @ExceptionHandler({org.springframework.web.client.RestClientException.class})
  ResponseEntity<Object> upstream(RuntimeException e, HttpServletRequest r) {
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "A required service is unavailable. Please retry shortly.",
        r.getRequestURI(),
        Map.of());
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    String message = "The request could not be processed. Check the input and try again.";
    if (e instanceof MethodArgumentNotValidException invalid) {
      invalid
          .getBindingResult()
          .getFieldErrors()
          .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
      message = "Validation failed";
    } else if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
      message = "Malformed request body. Check required fields, dates and value types.";
    } else if (e instanceof org.springframework.web.bind.MissingRequestHeaderException missing) {
      message = "Required header is missing: " + missing.getHeaderName();
    } else if (e
        instanceof org.springframework.web.bind.MissingServletRequestParameterException missing) {
      message = "Required parameter is missing: " + missing.getParameterName();
    }
    String path = ((ServletWebRequest) request).getRequest().getRequestURI();
    return new ResponseEntity<>(
        new ErrorBody(
            Instant.now(),
            status.value(),
            HttpStatus.valueOf(status.value()).getReasonPhrase(),
            message,
            path,
            fields),
        headers,
        status);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "The request could not be completed. Please retry or contact the service administrator.",
        r.getRequestURI(),
        Map.of());
  }

  private ResponseEntity<Object> response(
      HttpStatus status, String message, String path, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(
            new ErrorBody(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path, fields));
  }
}
