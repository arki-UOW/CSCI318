package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.domain.CalendarEntry.*;
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

  public CalendarApplicationService(
      CalendarEntryRepository entries, CompletedStudyBlockEvents events) {
    this.entries = entries;
    this.events = events;
  }

  public List<EntryResponse> list(UUID ownerId, LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || to.isBefore(from)
        || ChronoUnit.DAYS.between(from, to) > 370) {
      throw new IllegalArgumentException("Choose a valid calendar range of at most 370 days");
    }
    return entries
        .findByOwnerIdAndStartAtBetweenOrderByStartAt(
            ownerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1))
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public EntryResponse create(UUID ownerId, SaveRequest request) {
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
  public EntryResponse update(UUID ownerId, UUID id, SaveRequest request) {
    CalendarEntry entry = find(ownerId, id);
    if (entry.getStatus() == EntryStatus.COMPLETED) {
      throw new IllegalStateException(
          "Completed study history cannot be edited. Delete it and record a correction.");
    }
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
    CalendarEntry next = createNextReview(completed);
    return new CompletionResponse(response(completed), next == null ? null : response(next));
  }

  @Transactional
  public void replaceAiPlan(UUID ownerId, UUID planId, List<PlanItem> items) {
    entries.deleteByOwnerIdAndOriginAndStatus(ownerId, Origin.AI_PLAN, EntryStatus.PLANNED);
    Map<LocalDate, Integer> dayMinutes = new HashMap<>();
    Map<LocalDate, Integer> dayCount = new HashMap<>();
    items.forEach(
        item -> {
          dayMinutes.merge(item.date(), item.allocatedMinutes(), Integer::sum);
          dayCount.merge(item.date(), 1, Integer::sum);
        });
    Map<LocalDate, Integer> usedMinutes = new HashMap<>();
    for (PlanItem item : items) {
      int offset = usedMinutes.getOrDefault(item.date(), 0);
      int total = dayMinutes.get(item.date());
      int gap =
          dayCount.get(item.date()) <= 1
              ? 0
              : Math.min(10, (1440 - total) / (dayCount.get(item.date()) - 1));
      int firstMinute = Math.min(18 * 60, 1440 - total - gap * (dayCount.get(item.date()) - 1));
      LocalDateTime startAt = item.date().atStartOfDay().plusMinutes(firstMinute + offset);
      LocalDateTime endAt = startAt.plusMinutes(item.allocatedMinutes());
      EntryType type = item.repetitionStage() > 0 ? EntryType.REVIEW : EntryType.STUDY_SESSION;
      String description =
          item.repetitionStage() > 0
              ? "Pre-planned spaced review "
                  + item.repetitionStage()
                  + " before the assessment due date"
              : "Deadline-planned study block based on estimated workload";
      entries.save(
          new CalendarEntry(
              ownerId,
              item.subjectId(),
              item.assessmentId(),
              planId,
              null,
              item.title(),
              description,
              type,
              startAt,
              endAt,
              Origin.AI_PLAN,
              false,
              item.repetitionStage()));
      usedMinutes.merge(item.date(), item.allocatedMinutes() + gap, Integer::sum);
    }
  }

  private CalendarEntry createNextReview(CalendarEntry completed) {
    if (!completed.isSpacedRepetition()
        || completed.getRepetitionStage() >= REVIEW_INTERVAL_DAYS.length) {
      return null;
    }
    int stage = completed.getRepetitionStage();
    int delay = REVIEW_INTERVAL_DAYS[stage];
    Duration duration = Duration.between(completed.getStartAt(), completed.getEndAt());
    LocalDateTime nextStart =
        LocalDate.now().plusDays(delay).atTime(completed.getStartAt().toLocalTime());
    String baseTitle = completed.getTitle().replaceFirst("^Review: ", "");
    return entries.save(
        new CalendarEntry(
            completed.getOwnerId(),
            completed.getSubjectId(),
            completed.getAssessmentId(),
            completed.getPlanId(),
            completed.getId(),
            "Review: " + baseTitle,
            "Spaced review " + (stage + 1) + " of " + REVIEW_INTERVAL_DAYS.length,
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
