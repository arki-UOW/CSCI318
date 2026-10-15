package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AvailabilityChatRequest;
import au.edu.uow.csci318.planning.dto.PlanningDtos.AvailabilityChatResponse;
import au.edu.uow.csci318.planning.dto.PlanningDtos.TimeSlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class AvailabilityAssistant {
  private final ObjectMapper json;
  private final ConfiguredPlanningChatModel configuredModel;

  AvailabilityAssistant(ObjectMapper json, ConfiguredPlanningChatModel configuredModel) {
    this.json = json;
    this.configuredModel = configuredModel;
  }

  AvailabilityChatResponse assist(AvailabilityChatRequest request) {
    ConfiguredPlanningChatModel.Selection selection =
        configuredModel
            .selection()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Gemini is not available inside Planning Service. Check .env, then rebuild"
                            + " the containers."));
    try {
      String prompt =
          """
          You are a concise study-planning availability assistant. Convert the user's natural-language
          availability into a complete updated weekly schedule. Return ONLY one JSON object with:
          reply, days, readyToPlan.

          days is an array containing objects with dayOfWeek and slots. dayOfWeek must be one of
          MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY. Never calculate or return dates.
          The server maps each weekday to the supplied seven-day calendar. Each slot has start and end in 24-hour
          HH:mm format. Keep unchanged slots from the current schedule unless the user changes or clears
          them. Use the weekday names stated by the user. Never create times the
          user did not state. If a time is ambiguous, ask a short follow-up in reply and set readyToPlan
          false. Set readyToPlan true when at least one usable slot exists and no clarification is needed.
          Keep reply friendly and under 45 words.
          """
              + "\nWeek: "
              + request.weekStart()
              + " to "
              + request.weekStart().plusDays(6)
              + "\nAuthoritative calendar (weekday to date): "
              + calendar(request.weekStart())
              + "\nCurrent availability: "
              + json.writeValueAsString(request.availability())
              + "\nUser message: "
              + request.message();
      String raw =
          selection
              .model()
              .chat(prompt)
              .replaceFirst("(?s)^```(?:json)?\\s*", "")
              .replaceFirst("(?s)\\s*```$", "");
      AssistantPayload payload = json.readValue(raw, AssistantPayload.class);
      Map<LocalDate, List<TimeSlot>> availability =
          normalise(request.weekStart(), request.availability(),
              resolveDays(request.weekStart(), payload.readyToPlan() ? payload.days()
                  : payload.days() == null ? List.of() : payload.days().stream()
                      .filter(day -> day != null && day.slots() != null && day.slots().isEmpty())
                      .toList()));
      Map<LocalDate, Integer> minutes = dailyMinutes(availability);
      boolean ready =
          payload.readyToPlan() && minutes.values().stream().mapToInt(Integer::intValue).sum() > 0;
      String reply = ready ? describe(availability) :
          payload.reply() == null || payload.reply().isBlank()
              ? "I updated your availability. Add another time or generate the plan when it looks"
                    + " right."
              : payload.reply().trim();
      return new AvailabilityChatResponse(
          reply, availability, minutes, ready, selection.provider());
    } catch (Exception exception) {
      throw new ProviderUnavailableException(
          ConfiguredPlanningChatModel.failureMessage(
              selection, exception, "understand that availability"),
          exception);
    }
  }

  private static Map<DayOfWeek, LocalDate> calendar(LocalDate start) {
    Map<DayOfWeek, LocalDate> dates = new LinkedHashMap<>();
    for (int day = 0; day < 7; day++) {
      LocalDate date = start.plusDays(day);
      dates.put(date.getDayOfWeek(), date);
    }
    return dates;
  }

  private static List<AvailabilityDay> resolveDays(LocalDate start, List<WeekdayAvailability> days) {
    if (days == null || days.size() > 7) {
      throw new IllegalArgumentException("Availability must contain up to seven named weekdays");
    }
    Map<DayOfWeek, LocalDate> dates = calendar(start);
    Set<DayOfWeek> seen = new HashSet<>();
    List<AvailabilityDay> resolved = new ArrayList<>();
    for (WeekdayAvailability day : days) {
      if (day == null || day.dayOfWeek() == null || day.slots() == null) {
        throw new IllegalArgumentException("Availability needs weekday names and time slots");
      }
      DayOfWeek weekday = DayOfWeek.valueOf(day.dayOfWeek().trim().toUpperCase(Locale.ROOT));
      if (!seen.add(weekday) || validSlots(day.slots()).size() != day.slots().size()) {
        throw new IllegalArgumentException("Availability contains repeated weekdays or invalid time slots");
      }
      resolved.add(new AvailabilityDay(dates.get(weekday), day.slots()));
    }
    return resolved;
  }

  private static String describe(Map<LocalDate, List<TimeSlot>> availability) {
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH);
    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
    return "Your availability: " + availability.entrySet().stream()
        .filter(entry -> !entry.getValue().isEmpty())
        .map(entry -> entry.getKey().format(dateFormat) + " " + entry.getValue().stream()
            .map(slot -> slot.start().format(timeFormat) + "–" + slot.end().format(timeFormat))
            .collect(Collectors.joining(", ")))
        .collect(Collectors.joining("; ")) + ". Review these times before building your plan.";
  }

  static Map<LocalDate, List<TimeSlot>> normalise(
      LocalDate weekStart, Map<LocalDate, List<TimeSlot>> current, List<AvailabilityDay> updates) {
    Map<LocalDate, List<TimeSlot>> result = new LinkedHashMap<>();
    for (int day = 0; day < 7; day++) {
      LocalDate date = weekStart.plusDays(day);
      result.put(date, validSlots(current == null ? null : current.get(date)));
    }
    if (updates != null) {
      for (AvailabilityDay update : updates) {
        if (update == null
            || update.date() == null
            || update.date().isBefore(weekStart)
            || update.date().isAfter(weekStart.plusDays(6))) {
          continue;
        }
        result.put(update.date(), validSlots(update.slots()));
      }
    }
    return result;
  }

  static Map<LocalDate, Integer> dailyMinutes(Map<LocalDate, List<TimeSlot>> availability) {
    Map<LocalDate, Integer> result = new LinkedHashMap<>();
    availability.forEach(
        (date, slots) ->
            result.put(
                date,
                slots.stream()
                    .mapToInt(
                        slot ->
                            Math.toIntExact(Duration.between(slot.start(), slot.end()).toMinutes()))
                    .sum()));
    return result;
  }

  private static List<TimeSlot> validSlots(List<TimeSlot> supplied) {
    if (supplied == null) {
      return List.of();
    }
    List<TimeSlot> sorted =
        supplied.stream()
            .filter(
                slot ->
                    slot != null
                        && slot.start() != null
                        && slot.end() != null
                        && slot.end().isAfter(slot.start())
                        && Duration.between(slot.start(), slot.end()).toMinutes() >= 15
                        && Duration.between(slot.start(), slot.end()).toHours() <= 12)
            .sorted(Comparator.comparing(TimeSlot::start))
            .toList();
    List<TimeSlot> nonOverlapping = new ArrayList<>();
    for (TimeSlot slot : sorted) {
      if (nonOverlapping.isEmpty() || !slot.start().isBefore(nonOverlapping.getLast().end())) {
        nonOverlapping.add(slot);
      }
    }
    return List.copyOf(nonOverlapping);
  }

  record AssistantPayload(String reply, List<WeekdayAvailability> days, boolean readyToPlan) {}

  @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
  record WeekdayAvailability(String dayOfWeek, List<TimeSlot> slots) {}

  record AvailabilityDay(LocalDate date, List<TimeSlot> slots) {}
}
