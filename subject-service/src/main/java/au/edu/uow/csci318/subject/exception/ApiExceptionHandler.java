package au.edu.uow.csci318.subject.exception;

import au.edu.uow.csci318.subject.infrastructure.SubjectApplicationService.ServiceDependencyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
 record ErrorBody(Instant timestamp,int status,String error,String message,String path,Map<String,String> validationErrors){}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<ErrorBody> bad(RuntimeException e,HttpServletRequest r){return body(HttpStatus.BAD_REQUEST,e,r,null);}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ErrorBody> missing(RuntimeException e,HttpServletRequest r){return body(HttpStatus.NOT_FOUND,e,r,null);}
 @ExceptionHandler(ServiceDependencyException.class) ResponseEntity<ErrorBody> unavailable(RuntimeException e,HttpServletRequest r){return body(HttpStatus.SERVICE_UNAVAILABLE,e,r,null);}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e,HttpServletRequest r){Map<String,String>v=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(x->v.put(x.getField(),x.getDefaultMessage()));return body(HttpStatus.BAD_REQUEST,new IllegalArgumentException("Validation failed"),r,v);}
 private ResponseEntity<ErrorBody> body(HttpStatus s,RuntimeException e,HttpServletRequest r,Map<String,String>v){return ResponseEntity.status(s).body(new ErrorBody(Instant.now(),s.value(),s.getReasonPhrase(),e.getMessage(),r.getRequestURI(),v));}
}
