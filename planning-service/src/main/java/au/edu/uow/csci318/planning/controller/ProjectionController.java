package au.edu.uow.csci318.planning.controller;

import au.edu.uow.csci318.planning.application.DashboardQueryService;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient;
import au.edu.uow.csci318.planning.infrastructure.stream.DashboardPushHub;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/planning")
@CrossOrigin
public class ProjectionController {
  private final DashboardQueryService queries;
  private final DashboardPushHub push;
  private final IdentityClient identity;

  public ProjectionController(
      DashboardQueryService queries, DashboardPushHub push, IdentityClient identity) {
    this.queries = queries;
    this.push = push;
    this.identity = identity;
  }

  @GetMapping("/workload")
  public WorkloadSummary workload(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone) {
    return queries.workload(identity.require(auth), ZoneId.of(zone));
  }

  @GetMapping("/this-week")
  public ThisWeek week(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone) {
    return queries.snapshot(identity.require(auth), ZoneId.of(zone)).week();
  }

  @GetMapping("/stream-status")
  public StreamStatus status(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone) {
    return queries.snapshot(identity.require(auth), ZoneId.of(zone)).stream();
  }

  @GetMapping("/progress")
  public List<ProgressSummary> progress(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone,
      @RequestParam(required = false) LocalDate weekOf) {
    return queries.progress(identity.require(auth), ZoneId.of(zone), weekOf);
  }

  @GetMapping("/progress/{subjectId}")
  public ProgressSummary progress(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone,
      @PathVariable UUID subjectId,
      @RequestParam(required = false) LocalDate weekOf) {
    return progress(auth, zone, weekOf).stream()
        .filter(item -> item.subjectId().equals(subjectId))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException("Subject progress has not been projected yet"));
  }

  @GetMapping(value = "/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader("Authorization") String auth,
      @RequestHeader(value = "X-Study-Timezone", defaultValue = "UTC") String zone,
      HttpServletResponse response) {
    UUID owner = identity.require(auth);
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("X-Accel-Buffering", "no");
    return push.subscribe(owner, ZoneId.of(zone));
  }
}
