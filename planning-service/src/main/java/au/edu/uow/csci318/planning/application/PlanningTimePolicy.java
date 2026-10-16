package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Rules for new plans; historical records remain readable. */
public final class PlanningTimePolicy {
  private PlanningTimePolicy() {}

  public static void requireFutureStart(LocalDate start, LocalDateTime now) {
    if (start == null || start.isBefore(now.toLocalDate()))
      throw new IllegalArgumentException("Choose today or a future date to plan your next seven days.");
  }

  public static void validate(PlanRequest request, LocalDateTime now) {
    requireFutureStart(request.startDate(), now);
    if (request.availabilitySlots() != null) {
      request.availabilitySlots().forEach((date, slots) -> {
        if (date != null && slots != null) for (var slot : slots) {
          if (slot != null && slot.start() != null && date.atTime(slot.start()).isBefore(now))
            throw new IllegalArgumentException("Some availability has already passed. Choose future study times.");
        }
      });
    }
    int todayMinutes = request.dailyAvailabilityMinutes().getOrDefault(now.toLocalDate(), 0);
    long remainingToday = ChronoUnit.MINUTES.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
    if (todayMinutes > remainingToday)
      throw new IllegalArgumentException("Only " + remainingToday + " minutes remain today. Reduce today's availability or start tomorrow.");
  }
}
