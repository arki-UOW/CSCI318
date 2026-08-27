package au.edu.uow.csci318.account.exception;

import au.edu.uow.csci318.account.application.AccountApplicationService.DuplicateUsernameException;
import au.edu.uow.csci318.account.application.AccountApplicationService.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError unauthorized(UnauthorizedException exception) { return error(401, "Unauthorized", exception.getMessage(), null); }

    @ExceptionHandler(DuplicateUsernameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(DuplicateUsernameException exception) { return error(409, "Conflict", exception.getMessage(), null); }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError badRequest(RuntimeException exception) { return error(400, "Bad Request", exception.getMessage(), null); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(item -> fields.put(item.getField(), item.getDefaultMessage()));
        return error(400, "Bad Request", "Check the highlighted account fields", fields);
    }

    private ApiError error(int status, String label, String message, Map<String, String> fields) {
        return new ApiError(Instant.now(), status, label, message, fields);
    }

    record ApiError(Instant timestamp, int status, String error, String message,
                    Map<String, String> validationErrors) {}
}
