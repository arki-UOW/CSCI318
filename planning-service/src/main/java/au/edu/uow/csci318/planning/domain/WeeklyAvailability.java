package au.edu.uow.csci318.planning.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** Immutable, self-validating value object for the recurring weekly availability template. */
public record WeeklyAvailability(Map<DayOfWeek, Integer> minutes) {
  public WeeklyAvailability {
    Objects.requireNonNull(minutes, "Availability is required");
    if (minutes.entrySet().stream()
        .anyMatch(
            entry ->
                entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue() < 0
                    || entry.getValue() > 1440)) {
      throw new IllegalArgumentException("Daily availability must be 0-1440 minutes");
    }
    minutes = Map.copyOf(minutes);
  }

  public static WeeklyAvailability from(LocalDate start, Map<LocalDate, Integer> dates) {
    Objects.requireNonNull(start, "Start date is required");
    Objects.requireNonNull(dates, "Availability is required");
    Map<DayOfWeek, Integer> week = new EnumMap<>(DayOfWeek.class);
    dates.forEach(
        (date, value) -> {
          if (date == null || date.isBefore(start) || date.isAfter(start.plusDays(6))) {
            throw new IllegalArgumentException("Availability must lie within the template week");
          }
          if (value == null)
            throw new IllegalArgumentException("Availability minutes are required");
          week.put(date.getDayOfWeek(), value);
        });
    return new WeeklyAvailability(week);
  }
}
