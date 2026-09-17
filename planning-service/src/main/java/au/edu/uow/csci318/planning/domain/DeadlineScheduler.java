package au.edu.uow.csci318.planning.domain;

import au.edu.uow.csci318.planning.domain.stream.AssessmentSnapshot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a deterministic deadline plan. The LLM is deliberately kept out of minute allocation:
 * availability, due dates and estimated workload are factual inputs and are easier to validate
 * here.
 */
public class DeadlineScheduler {
  private static final int[] REPETITION_OFFSETS = {0, 1, 3, 7, 14, 30};
  private static final int MAX_HORIZON_DAYS = 365;
  private static final int DEFAULT_ESTIMATED_MINUTES = 120;
  private static final int TARGET_SESSION_MINUTES = 25;
  private static final int MAX_SESSION_MINUTES = 90;

  public Schedule schedule(
      LocalDate startDate,
      WeeklyAvailability availability,
      List<AssessmentSnapshot> work,
      Map<UUID, Integer> completedMinutes) {
    List<AssessmentSnapshot> assessments = prioritised(work, startDate);
    if (assessments.isEmpty()) {
      throw new IllegalArgumentException(
          "No usable incomplete assessments were found. Add an assessment with a due date first.");
    }

    Map<DayOfWeek, Integer> weeklyAvailability = availability.minutes();
    if (weeklyAvailability.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
      throw new IllegalArgumentException(
          "Add at least one available study period before generating a plan.");
    }

    LocalDate maximumEnd = startDate.plusDays(MAX_HORIZON_DAYS);
    if (assessments.stream()
        .anyMatch(item -> item.dueDate() != null && item.dueDate().isAfter(maximumEnd))) {
      throw new IllegalArgumentException(
          "Due dates more than one year away are not supported. Choose a later start date.");
    }
    LocalDate endDate =
        assessments.stream()
            .map(AssessmentSnapshot::dueDate)
            .filter(date -> date != null && !date.isBefore(startDate))
            .map(date -> date.isAfter(maximumEnd) ? maximumEnd : date)
            .max(LocalDate::compareTo)
            .orElse(startDate.plusDays(6));

    Map<LocalDate, Integer> usedMinutes = new HashMap<>();
    List<ScheduledSession> items = new ArrayList<>();
    int requestedMinutes = 0;
    int scheduledMinutes = 0;

    for (AssessmentSnapshot assessment : assessments) {
      LocalDate dueDate =
          assessment.dueDate() == null
              ? startDate.plusDays(6)
              : assessment.dueDate().isAfter(maximumEnd) ? maximumEnd : assessment.dueDate();
      if (dueDate.isBefore(startDate)) continue;

      int totalMinutes =
          assessment.estimatedMinutes() == null || assessment.estimatedMinutes() <= 0
              ? DEFAULT_ESTIMATED_MINUTES
              : assessment.estimatedMinutes();
      totalMinutes = Math.max(0, totalMinutes - completedMinutes.getOrDefault(assessment.id(), 0));
      if (totalMinutes == 0) continue;
      requestedMinutes += totalMinutes;
      LocalDate lastStudyDate = dueDate.isAfter(startDate) ? dueDate.minusDays(1) : dueDate;
      List<LocalDate> repetitions = repetitionDates(startDate, lastStudyDate, totalMinutes);
      int remaining = totalMinutes;
      LocalDate earliest = startDate;

      for (int index = 0; index < repetitions.size() && remaining > 0; index++) {
        int sessionsLeft = repetitions.size() - index;
        int targetMinutes = (int) Math.ceil((double) remaining / sessionsLeft);
        Allocation allocation =
            allocate(
                assessment,
                index,
                repetitions.get(index),
                earliest,
                lastStudyDate,
                targetMinutes,
                weeklyAvailability,
                usedMinutes);
        items.addAll(allocation.items());
        remaining -= allocation.minutes();
        scheduledMinutes += allocation.minutes();
        if (allocation.lastDate() != null) earliest = allocation.lastDate().plusDays(1);
      }
      // Spacing is a preference, not permission to discard usable capacity.
      // Fill any remaining estimated work before the deadline and report only genuine shortfalls.
      if (remaining > 0) {
        Allocation extra =
            allocate(
                assessment,
                repetitions.size(),
                lastStudyDate,
                startDate,
                lastStudyDate,
                remaining,
                weeklyAvailability,
                usedMinutes);
        items.addAll(extra.items());
        scheduledMinutes += extra.minutes();
      }
    }

    items.sort(
        Comparator.comparing(ScheduledSession::date)
            .thenComparing(ScheduledSession::repetitionStage)
            .thenComparing(ScheduledSession::title));
    return new Schedule(List.copyOf(items), endDate, requestedMinutes, scheduledMinutes);
  }

  private Allocation allocate(
      AssessmentSnapshot assessment,
      int repetitionStage,
      LocalDate preferred,
      LocalDate earliest,
      LocalDate latest,
      int targetMinutes,
      Map<DayOfWeek, Integer> weeklyAvailability,
      Map<LocalDate, Integer> usedMinutes) {
    if (earliest.isAfter(latest) || targetMinutes <= 0) return Allocation.empty();
    List<LocalDate> candidates =
        earliest
            .datesUntil(latest.plusDays(1))
            .sorted(
                Comparator.comparingLong(
                    date -> Math.abs(ChronoUnit.DAYS.between(preferred, date))))
            .toList();
    List<ScheduledSession> items = new ArrayList<>();
    int allocated = 0;
    LocalDate lastDate = null;

    for (LocalDate date : candidates) {
      if (allocated >= targetMinutes) break;
      int capacity =
          weeklyAvailability.getOrDefault(date.getDayOfWeek(), 0)
              - usedMinutes.getOrDefault(date, 0);
      if (capacity <= 0) continue;
      while (capacity > 0 && allocated < targetMinutes) {
        int minutes = Math.min(Math.min(targetMinutes - allocated, capacity), MAX_SESSION_MINUTES);
        String title =
            repetitionStage == 0
                ? assessment.title()
                : "Review " + repetitionStage + ": " + assessment.title();
        items.add(
            new ScheduledSession(
                date, assessment.subjectId(), assessment.id(), title, minutes, repetitionStage));
        usedMinutes.merge(date, minutes, Integer::sum);
        allocated += minutes;
        capacity -= minutes;
        if (lastDate == null || date.isAfter(lastDate)) lastDate = date;
      }
    }
    return new Allocation(items, allocated, lastDate);
  }

  private List<LocalDate> repetitionDates(LocalDate start, LocalDate latest, int totalMinutes) {
    LinkedHashSet<LocalDate> raw = new LinkedHashSet<>();
    for (int offset : REPETITION_OFFSETS) {
      LocalDate date = start.plusDays(offset);
      if (!date.isAfter(latest)) raw.add(date);
    }
    for (LocalDate date = start.plusDays(60); !date.isAfter(latest); date = date.plusDays(30)) {
      raw.add(date);
    }
    raw.add(latest);
    List<LocalDate> dates = new ArrayList<>(raw);
    int wanted =
        Math.min(
            dates.size(),
            Math.max(1, (int) Math.ceil((double) totalMinutes / TARGET_SESSION_MINUTES)));
    if (wanted == dates.size()) return dates;
    if (wanted == 1) return List.of(dates.get(dates.size() - 1));

    LinkedHashSet<LocalDate> selected = new LinkedHashSet<>();
    for (int index = 0; index < wanted; index++) {
      int position = (int) Math.round((double) index * (dates.size() - 1) / (wanted - 1));
      selected.add(dates.get(position));
    }
    return List.copyOf(selected);
  }

  private List<AssessmentSnapshot> prioritised(List<AssessmentSnapshot> work, LocalDate startDate) {
    return work.stream()
        .filter(
            assessment -> assessment.dueDate() == null || !assessment.dueDate().isBefore(startDate))
        .sorted(
            Comparator.comparing(
                    (AssessmentSnapshot assessment) ->
                        assessment.dueDate() == null ? LocalDate.MAX : assessment.dueDate())
                .thenComparing(assessment -> "HIGH".equals(assessment.priority()) ? 0 : 1)
                .thenComparingDouble(
                    assessment -> assessment.weighting() == null ? 0 : -assessment.weighting()))
        .toList();
  }

  public record ScheduledSession(
      LocalDate date,
      UUID subjectId,
      UUID assessmentId,
      String title,
      int allocatedMinutes,
      int repetitionStage) {}

  public record Schedule(
      List<ScheduledSession> items, LocalDate endDate, int requestedMinutes, int scheduledMinutes) {
    public int unscheduledMinutes() {
      return Math.max(0, requestedMinutes - scheduledMinutes);
    }
  }

  private record Allocation(List<ScheduledSession> items, int minutes, LocalDate lastDate) {
    static Allocation empty() {
      return new Allocation(List.of(), 0, null);
    }
  }
}
