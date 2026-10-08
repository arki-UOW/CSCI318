package au.edu.uow.csci318.planning.domain;

import java.time.*;
import java.util.*;

/**
 * Exact recurring availability, separate from the daily capacity used by the deadline scheduler.
 */
public record WeeklyTimeSlots(Map<DayOfWeek, List<Window>> days) {
  public WeeklyTimeSlots {
    Map<DayOfWeek, List<Window>> checked = new EnumMap<>(DayOfWeek.class);
    Objects.requireNonNull(days, "Time slots are required")
        .forEach(
            (day, windows) -> {
              if (day == null || windows == null || windows.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                    "Availability days and time slots cannot be null");
              }
              List<Window> sorted =
                  windows.stream().sorted(Comparator.comparing(Window::start)).toList();
              for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).start().isBefore(sorted.get(i - 1).end())) {
                  throw new IllegalArgumentException("Availability time slots cannot overlap");
                }
              }
              checked.put(day, sorted);
            });
    days = Map.copyOf(checked);
  }

  public static WeeklyTimeSlots from(LocalDate start, Map<LocalDate, List<Window>> dates) {
    Map<DayOfWeek, List<Window>> days = new EnumMap<>(DayOfWeek.class);
    Objects.requireNonNull(dates, "Time slots are required")
        .forEach(
            (date, windows) -> {
              if (date == null || date.isBefore(start) || date.isAfter(start.plusDays(6))) {
                throw new IllegalArgumentException("Time slots must lie within the template week");
              }
              days.put(date.getDayOfWeek(), windows);
            });
    return new WeeklyTimeSlots(days);
  }

  public int minutes(DayOfWeek day) {
    return days.getOrDefault(day, List.of()).stream().mapToInt(Window::minutes).sum();
  }

  /**
   * Subtract existing commitments, including ones spanning midnight, from the available intervals.
   */
  public List<Interval> free(LocalDate date, List<Interval> occupied) {
    List<Interval> free =
        new ArrayList<>(
            days.getOrDefault(date.getDayOfWeek(), List.of()).stream()
                .map(window -> new Interval(date.atTime(window.start()), date.atTime(window.end())))
                .toList());
    for (Interval busy : occupied) {
      List<Interval> next = new ArrayList<>();
      for (Interval slot : free) {
        if (!busy.start().isBefore(slot.end()) || !busy.end().isAfter(slot.start())) {
          next.add(slot);
        } else {
          if (busy.start().isAfter(slot.start()))
            next.add(new Interval(slot.start(), busy.start()));
          if (busy.end().isBefore(slot.end())) next.add(new Interval(busy.end(), slot.end()));
        }
      }
      free = next;
    }
    return List.copyOf(free);
  }

  public record Window(LocalTime start, LocalTime end) {
    public Window {
      if (start == null
          || end == null
          || !end.isAfter(start)
          || start.getSecond() != 0
          || end.getSecond() != 0
          || start.getNano() != 0
          || end.getNano() != 0) {
        throw new IllegalArgumentException(
            "Availability slots need whole-minute times with end after start");
      }
    }

    public int minutes() {
      return (int) Duration.between(start, end).toMinutes();
    }
  }

  public record Interval(LocalDateTime start, LocalDateTime end) {}
}
