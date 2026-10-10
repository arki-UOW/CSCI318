package au.edu.uow.csci318.planning.controller;

import au.edu.uow.csci318.planning.application.PlanningApplicationService;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planning")
@CrossOrigin
public class PlanningController {
  private final PlanningApplicationService service;
  private final IdentityClient identity;

  public PlanningController(PlanningApplicationService service, IdentityClient identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping("/ai/status")
  public AiStatus aiStatus(@RequestHeader("Authorization") String authorization) {
    identity.require(authorization);
    return service.aiStatus();
  }

  @PostMapping("/availability/chat")
  public AvailabilityChatResponse updateAvailability(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody AvailabilityChatRequest request) {
    identity.require(authorization);
    return service.updateAvailability(request);
  }

  @GetMapping("/plans")
  public java.util.List<PlanResponse> history(
      @RequestHeader("Authorization") String authorization) {
    return service.history(identity.require(authorization));
  }

  @GetMapping("/plans/{id}")
  public PlanResponse get(
      @RequestHeader("Authorization") String authorization, @PathVariable("id") UUID id) {
    return service.get(identity.require(authorization), id);
  }

  @GetMapping("/plans/latest")
  public ResponseEntity<PlanResponse> latest(@RequestHeader("Authorization") String authorization) {
    return service
        .latest(identity.require(authorization))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @PostMapping("/plans")
  @ResponseStatus(HttpStatus.CREATED)
  public PlanResponse generate(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String timezone,
      @Valid @RequestBody PlanRequest request) {
    return service.generate(
        identity.require(authorization), authorization, ZoneId.of(timezone), request);
  }

  @PostMapping("/plans/{id}/regenerate")
  @ResponseStatus(HttpStatus.CREATED)
  public PlanResponse regenerate(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String timezone,
      @PathVariable("id") UUID id,
      @Valid @RequestBody PlanRequest request) {
    return service.regenerate(
        identity.require(authorization), authorization, ZoneId.of(timezone), id, request);
  }
}
