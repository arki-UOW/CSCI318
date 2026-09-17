package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.stream.*;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** CQRS query facade. Academic data comes only from local Kafka-derived projections, never REST. */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class DashboardQueryService implements StudyHistory {
  private final ProjectionStore projections;
  private final StudyPlanRepository plans;
  private final ObjectMapper json;
  private final Clock clock;

  public DashboardQueryService(
      ProjectionStore projections, StudyPlanRepository plans, ObjectMapper json, Clock clock) {
    this.projections = projections;
    this.plans = plans;
    this.json = json;
    this.clock = clock;
  }

  public StreamSnapshot snapshot(UUID ownerId, ZoneId zone) {
    WorkloadState workload = projections.workload(ownerId).orElse(WorkloadState.empty());
    List<ProgressState> progress = projections.progress(ownerId);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    LocalDate monday = today.with(DayOfWeek.MONDAY);
    LocalDate sunday = monday.plusDays(6);
    List<AssessmentSnapshot> outstanding = workload.outstanding();
    List<AssessmentView> due =
        outstanding.stream()
            .filter(item -> between(item.dueDate(), monday, sunday))
            .sorted(Comparator.comparing(AssessmentSnapshot::dueDate))
            .map(this::view)
            .toList();
    List<AssessmentView> upcoming =
        outstanding.stream()
            .filter(item -> item.dueDate() != null && item.dueDate().isAfter(sunday))
            .sorted(Comparator.comparing(AssessmentSnapshot::dueDate))
            .limit(5)
            .map(this::view)
            .toList();
    List<StudyProgress> study =
        progress.stream()
            .sorted(Comparator.comparing(ProgressState::subjectId))
            .map(
                state -> {
                  int done = state.weeklyMinutes().getOrDefault(monday, 0);
                  int target =
                      state.subject() == null ? 0 : state.subject().weeklyStudyTargetMinutes();
                  return new StudyProgress(
                      state.subjectId(),
                      done,
                      target,
                      Math.max(0, target - done),
                      WorkloadClassification.progress(done, target));
                })
            .toList();
    Set<UUID> incompleteIds = new HashSet<>();
    outstanding.forEach(item -> incompleteIds.add(item.id()));
    List<PlanItem> planned = weekPlan(ownerId, monday, sunday, incompleteIds);
    WorkloadSummary summary = workload(workload, today);
    Instant updated =
        progress.stream()
            .map(ProgressState::updatedAt)
            .reduce(workload.updatedAt(), (left, right) -> left.isAfter(right) ? left : right);
    long progressRevision = progress.stream().mapToLong(ProgressState::revision).sum();
    StreamStatus status =
        new StreamStatus(
            workload.revision(),
            progressRevision,
            updated,
            "KAFKA_STREAMS",
            workload.revision() > 0 || progressRevision > 0);
    return new StreamSnapshot(
        new ThisWeek(monday, sunday, due, upcoming, summary, study, planned), status);
  }

  public WorkloadSummary workload(UUID ownerId, ZoneId zone) {
    return snapshot(ownerId, zone).week().workload();
  }

  public List<ProgressSummary> progress(UUID ownerId, ZoneId zone, LocalDate weekOf) {
    LocalDate monday =
        (weekOf == null ? LocalDate.now(clock.withZone(zone)) : weekOf).with(DayOfWeek.MONDAY);
    return projections.progress(ownerId).stream()
        .map(
            state -> {
              int done = state.weeklyMinutes().getOrDefault(monday, 0);
              SubjectSnapshot subject = state.subject();
              int target = subject == null ? 0 : subject.weeklyStudyTargetMinutes();
              return new ProgressSummary(
                  state.subjectId(),
                  subject == null ? null : subject.code(),
                  subject == null ? null : subject.name(),
                  state.totalMinutes(),
                  monday,
                  monday.plusDays(6),
                  done,
                  state.weeklySessionCounts().getOrDefault(monday, 0),
                  target,
                  Math.max(0, target - done),
                  WorkloadClassification.progress(done, target),
                  state.updatedAt());
            })
        .toList();
  }

  @Override
  public Map<UUID, Integer> completedAssessmentMinutes(UUID ownerId) {
    Map<UUID, Integer> completed = new HashMap<>();
    projections
        .progress(ownerId)
        .forEach(
            state ->
                state.sessions().values().stream()
                    .filter(fact -> !fact.deleted() && fact.snapshot().assessmentId() != null)
                    .forEach(
                        fact ->
                            completed.merge(
                                fact.snapshot().assessmentId(),
                                fact.snapshot().durationMinutes(),
                                Integer::sum)));
    return Map.copyOf(completed);
  }

  private WorkloadSummary workload(WorkloadState state, LocalDate today) {
    List<AssessmentSnapshot> upcoming =
        state.outstanding().stream()
            .filter(item -> between(item.dueDate(), today, today.plusDays(7)))
            .toList();
    int dueWeek =
        (int)
            state.outstanding().stream()
                .filter(item -> between(item.dueDate(), today, today.with(DayOfWeek.SUNDAY)))
                .count();
    int minutes =
        upcoming.stream()
            .map(AssessmentSnapshot::estimatedMinutes)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
    return new WorkloadSummary(
        state.incompleteCount(),
        dueWeek,
        upcoming.size(),
        state.estimatedMinutes(),
        state.highPriorityCount(),
        WorkloadClassification.classify(upcoming.size(), state.estimatedMinutes()),
        minutes);
  }

  private List<PlanItem> weekPlan(
      UUID ownerId, LocalDate from, LocalDate to, Set<UUID> incompleteIds) {
    return plans
        .findTopByOwnerIdOrderByCreatedAtDesc(ownerId)
        .map(
            plan -> {
              try {
                List<PlanItem> items =
                    json.readValue(plan.getItemsJson(), new TypeReference<>() {});
                return items.stream()
                    .filter(item -> incompleteIds.contains(item.assessmentId()))
                    .filter(item -> between(item.date(), from, to))
                    .toList();
              } catch (Exception exception) {
                throw new IllegalStateException("Stored plan is unreadable", exception);
              }
            })
        .orElse(List.of());
  }

  private AssessmentView view(AssessmentSnapshot item) {
    return new AssessmentView(
        item.id(),
        item.subjectId(),
        item.title(),
        item.type(),
        item.weighting(),
        item.dueDate(),
        item.dueWeek(),
        item.description(),
        item.estimatedMinutes(),
        item.priority(),
        item.status(),
        item.updatedAt());
  }

  private static boolean between(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }
}
