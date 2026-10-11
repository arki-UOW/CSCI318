package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.domain.CalendarEntry.*;
import au.edu.uow.csci318.planning.domain.WeeklyTimeSlots;
import au.edu.uow.csci318.planning.dto.CalendarDtos.*;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanItem;
import au.edu.uow.csci318.planning.infrastructure.CalendarEntryRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarApplicationService {
  private static final int[] REVIEW_INTERVAL_DAYS = {1, 3, 7, 14, 30};
  private final CalendarEntryRepository entries;
  private final CompletedStudyBlockEvents events;
  private final CalendarReferences references;

  public CalendarApplicationService(
      CalendarEntryRepository entries,
      CompletedStudyBlockEvents events,
      CalendarReferences references) {
    this.entries = entries;
    this.events = events;
    this.references = references;
  }

  public List<EntryResponse> list(UUID ownerId, LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || to.isBefore(from)
        || ChronoUnit.DAYS.between(from, to) > 370) {
      throw new IllegalArgumentException("Choose a valid calendar range of at most 370 days");
    }
    return entries
        .findOverlapping(ownerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public EntryResponse create(UUID ownerId, String authorization, SaveRequest request) {
    references.verify(authorization, request.subjectId(), request.assessmentId());
    return response(
        entries.save(
            new CalendarEntry(
                ownerId,
                request.subjectId(),
                request.assessmentId(),
                null,
                null,
                request.title(),
                request.description(),
                request.type(),
                request.startAt(),
                request.endAt(),
                Origin.MANUAL,
                request.spacedRepetition(),
                0)));
  }

  @Transactional
  public EntryResponse update(UUID ownerId, String authorization, UUID id, SaveRequest request) {
    CalendarEntry entry = find(ownerId, id);
    if (entry.getStatus() == EntryStatus.COMPLETED) {
      throw new IllegalStateException(
          "Completed study history cannot be edited. Delete it and record a correction.");
    }
    references.verify(authorization, request.subjectId(), request.assessmentId());
    entry.edit(
        request.title(),
        request.description(),
        request.type(),
        request.startAt(),
        request.endAt(),
        request.subjectId(),
        request.assessmentId(),
        request.spacedRepetition());
    return response(entry);
  }

  @Transactional
  public void delete(UUID ownerId, UUID id) {
    CalendarEntry entry = find(ownerId, id);
    events.publish(entry, true, ZoneId.of("UTC"));
    entries.delete(entry);
  }

  @Transactional
  public CompletionResponse complete(UUID ownerId, UUID id, ZoneId zone) {
    CalendarEntry completed = find(ownerId, id);
    completed.complete();
    events.publish(completed, false, zone);
    CalendarEntry next = createNextReview(completed, zone);
    return new CompletionResponse(response(completed), next == null ? null : response(next));
  }

  @Transactional
  public void replaceAiPlan(UUID ownerId, UUID planId, List<PlanItem> items) {
    replaceAiPlan(ownerId, planId, items, null);
  }

  @Transactional
  public void replaceAiPlan(
      UUID ownerId, UUID planId, List<PlanItem> items, WeeklyTimeSlots slots) {
    replaceWithinAvailability(ownerId, planId, items, slots);
  }

  private void replaceWithinAvailability(
      UUID ownerId, UUID planId, List<PlanItem> items, WeeklyTimeSlots slots) {
    if (items.isEmpty()) {
      entries.deleteByOwnerIdAndOriginAndStatus(ownerId, Origin.AI_PLAN, EntryStatus.PLANNED);
      return;
    }
    LocalDate first = items.stream().map(PlanItem::date).min(LocalDate::compareTo).orElseThrow();
    LocalDate last = items.stream().map(PlanItem::date).max(LocalDate::compareTo).orElseThrow();
    List<WeeklyTimeSlots.Interval> occupied =
        entries
            .findOverlapping(ownerId, first.atStartOfDay(), last.plusDays(1).atStartOfDay())
            .stream()
            .filter(
                entry ->
                    entry.getOrigin() != Origin.AI_PLAN || entry.getStatus() != EntryStatus.PLANNED)
            .map(entry -> new WeeklyTimeSlots.Interval(entry.getStartAt(), entry.getEndAt()))
            .toList();
    Map<LocalDate, Deque<WeeklyTimeSlots.Interval>> free = new HashMap<>();
    Map<LocalDate, Integer> dayMinutes = new HashMap<>();
    items.forEach(item -> dayMinutes.merge(item.date(), item.allocatedMinutes(), Integer::sum));
    List<CalendarEntry> replacement = new ArrayList<>();
    for (PlanItem item : items) {
      Deque<WeeklyTimeSlots.Interval> windows =
          free.computeIfAbsent(
              item.date(),
              date ->
                  new ArrayDeque<>(
                      slots == null
                          ? freeDay(date, dayMinutes.get(date), occupied)
                          : slots.free(date, occupied)));
      int remaining = item.allocatedMinutes();
      while (remaining > 0 && !windows.isEmpty()) {
        WeeklyTimeSlots.Interval window = windows.removeFirst();
        int minutes =
            Math.min(remaining, (int) Duration.between(window.start(), window.end()).toMinutes());
        if (minutes == 0) continue;
        LocalDateTime end = window.start().plusMinutes(minutes);
        replacement.add(
            new CalendarEntry(
                ownerId,
                item.subjectId(),
                item.assessmentId(),
                planId,
                null,
                calendarTitle(item.title()),
                slots == null
                    ? "Study scheduled around existing calendar commitments"
                    : "Study scheduled within your availability",
                item.repetitionStage() > 0 ? EntryType.REVIEW : EntryType.STUDY_SESSION,
                window.start(),
                end,
                Origin.AI_PLAN,
                false,
                item.repetitionStage()));
        remaining -= minutes;
        if (end.isBefore(window.end()))
          windows.addFirst(new WeeklyTimeSlots.Interval(end, window.end()));
      }
      if (remaining > 0) {
        throw new IllegalArgumentException(
            "Existing calendar commitments leave insufficient availability on "
                + item.date()
                + ". Adjust your availability or calendar and try again.");
      }
    }
    // Validate all placements first so a conflict never destroys the previous calendar plan.
    entries.deleteByOwnerIdAndOriginAndStatus(ownerId, Origin.AI_PLAN, EntryStatus.PLANNED);
    replacement.forEach(entries::save);
  }

  private List<WeeklyTimeSlots.Interval> freeDay(
      LocalDate date, int minutes, List<WeeklyTimeSlots.Interval> occupied) {
    // Daily minutes specify capacity, not clock times. Prefer evenings when the whole day's
    // allocation fits; otherwise use the entire day's free time in chronological order.
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    List<WeeklyTimeSlots.Interval> evening =
        WeeklyTimeSlots.subtract(
            List.of(new WeeklyTimeSlots.Interval(date.atTime(18, 0), end)), occupied);
    long eveningMinutes =
        evening.stream()
            .mapToLong(window -> Duration.between(window.start(), window.end()).toMinutes())
            .sum();
    return eveningMinutes >= minutes
        ? evening
        : WeeklyTimeSlots.subtract(
            List.of(new WeeklyTimeSlots.Interval(date.atStartOfDay(), end)), occupied);
  }

  private String calendarTitle(String title) {
    return title.length() <= 160 ? title : title.substring(0, 157) + "...";
  }

  private CalendarEntry createNextReview(CalendarEntry completed, ZoneId zone) {
    if (!completed.isSpacedRepetition()
        || completed.getRepetitionStage() >= REVIEW_INTERVAL_DAYS.length) {
      return null;
    }
    int stage = completed.getRepetitionStage();
    int delay = REVIEW_INTERVAL_DAYS[stage];
    Duration duration = Duration.between(completed.getStartAt(), completed.getEndAt());
    LocalDateTime nextStart =
        LocalDate.now(zone).plusDays(delay).atTime(completed.getStartAt().toLocalTime());
    String baseTitle = completed.getTitle().replaceFirst("^Review: ", "");
    return entries.save(
        new CalendarEntry(
            completed.getOwnerId(),
            completed.getSubjectId(),
            completed.getAssessmentId(),
            completed.getPlanId(),
            completed.getId(),
            calendarTitle("Review: " + baseTitle),
            "Spaced review "
                + (stage + 1)
                + " of "
                + REVIEW_INTERVAL_DAYS.length
                + ": "
                + baseTitle,
            EntryType.REVIEW,
            nextStart,
            nextStart.plus(duration),
            Origin.SPACED_REPETITION,
            true,
            stage + 1));
  }

  private CalendarEntry find(UUID ownerId, UUID id) {
    return entries
        .findByIdAndOwnerId(id, ownerId)
        .orElseThrow(() -> new NoSuchElementException("Calendar entry not found"));
  }

  private EntryResponse response(CalendarEntry entry) {
    return new EntryResponse(
        entry.getId(),
        entry.getSubjectId(),
        entry.getAssessmentId(),
        entry.getPlanId(),
        entry.getSourceEntryId(),
        entry.getTitle(),
        entry.getDescription(),
        entry.getType(),
        entry.getStartAt(),
        entry.getEndAt(),
        entry.getStatus(),
        entry.getOrigin(),
        entry.isSpacedRepetition(),
        entry.getRepetitionStage(),
        entry.getCreatedAt(),
        entry.getUpdatedAt(),
        entry.getCompletedAt());
  }
}
