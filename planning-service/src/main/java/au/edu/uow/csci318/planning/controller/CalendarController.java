package au.edu.uow.csci318.planning.controller;

import au.edu.uow.csci318.planning.application.CalendarApplicationService;
import au.edu.uow.csci318.planning.dto.CalendarDtos.*;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calendar")
@CrossOrigin
public class CalendarController {
  private final CalendarApplicationService service;
  private final IdentityClient identity;

  public CalendarController(CalendarApplicationService service, IdentityClient identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping
  public List<EntryResponse> list(
      @RequestHeader("Authorization") String authorization,
      @RequestParam("from") LocalDate from,
      @RequestParam("to") LocalDate to) {
    return service.list(identity.require(authorization), from, to);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EntryResponse create(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody SaveRequest request) {
    return service.create(identity.require(authorization), request);
  }

  @PatchMapping("/{id}")
  public EntryResponse update(
      @RequestHeader("Authorization") String authorization,
      @PathVariable("id") UUID id,
      @Valid @RequestBody SaveRequest request) {
    return service.update(identity.require(authorization), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestHeader("Authorization") String authorization, @PathVariable("id") UUID id) {
    service.delete(identity.require(authorization), id);
  }

  @PostMapping("/{id}/complete")
  public CompletionResponse complete(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String timezone,
      @PathVariable("id") UUID id) {
    return service.complete(identity.require(authorization), id, ZoneId.of(timezone));
  }
}
