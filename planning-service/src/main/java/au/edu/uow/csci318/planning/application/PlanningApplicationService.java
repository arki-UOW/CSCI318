package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningApplicationService {
  private final StudyPlanRepository plans;
  private final StudyPlanningAgent agent;
  private final PlanningTools tools;
  private final AvailabilityAssistant availabilityAssistant;
  private final ConfiguredPlanningChatModel configuredModel;
  private final CalendarApplicationService calendar;
  private final ObjectMapper json;

  public PlanningApplicationService(
      StudyPlanRepository plans,
      StudyPlanningAgent agent,
      PlanningTools tools,
      AvailabilityAssistant availabilityAssistant,
      ConfiguredPlanningChatModel configuredModel,
      CalendarApplicationService calendar,
      ObjectMapper json) {
    this.plans = plans;
    this.agent = agent;
    this.tools = tools;
    this.availabilityAssistant = availabilityAssistant;
    this.configuredModel = configuredModel;
    this.calendar = calendar;
    this.json = json;
  }

  @Transactional
  public PlanResponse generate(UUID ownerId, String authorization, PlanRequest request) {
    validatePeriod(request);
    StudyPlanningAgent.Schedule schedule = agent.generate(ownerId, request, authorization);
    validateItems(authorization, request, schedule.endDate(), schedule.items());
    try {
      int version =
          plans
              .findTopByOwnerIdOrderByCreatedAtDesc(ownerId)
              .map(existing -> existing.getVersion() + 1)
              .orElse(1);
      String explanation = explanation(schedule, "Generated");
      StudyPlan saved =
          plans.save(
              new StudyPlan(
                  ownerId,
                  request.startDate(),
                  schedule.endDate(),
                  version,
                  json.writeValueAsString(schedule.items()),
                  explanation));
      calendar.replaceAiPlan(ownerId, saved.getId(), schedule.items());
      return response(saved);
    } catch (Exception exception) {
      throw new IllegalStateException("Plan could not be stored", exception);
    }
  }

  @Transactional
  public PlanResponse regenerate(
      UUID ownerId, String authorization, UUID previousId, PlanRequest request) {
    StudyPlan old =
        plans
            .findByIdAndOwnerId(previousId, ownerId)
            .orElseThrow(() -> new NoSuchElementException("Study plan not found"));
    validatePeriod(request);
    StudyPlanningAgent.Schedule schedule = agent.generate(ownerId, request, authorization);
    validateItems(authorization, request, schedule.endDate(), schedule.items());
    try {
      List<PlanItem> before = json.readValue(old.getItemsJson(), new TypeReference<>() {});
      String explanation =
          explanation(schedule, "Regenerated") + " " + difference(before, schedule.items()) + ".";
      StudyPlan saved =
          plans.save(
              new StudyPlan(
                  ownerId,
                  request.startDate(),
                  schedule.endDate(),
                  old.getVersion() + 1,
                  json.writeValueAsString(schedule.items()),
                  explanation));
      calendar.replaceAiPlan(ownerId, saved.getId(), schedule.items());
      return response(saved);
    } catch (Exception exception) {
      throw new IllegalStateException("Plan could not be regenerated", exception);
    }
  }

  public AvailabilityChatResponse updateAvailability(AvailabilityChatRequest request) {
    return availabilityAssistant.assist(request);
  }

  public AiStatus aiStatus() {
    return configuredModel.status();
  }

  public Optional<PlanResponse> latest(UUID ownerId) {
    return plans.findTopByOwnerIdOrderByCreatedAtDesc(ownerId).map(this::response);
  }

  private void validatePeriod(PlanRequest request) {
    if (request.startDate() == null || request.dailyAvailabilityMinutes() == null) {
      throw new IllegalArgumentException("Start date and daily availability are required");
    }
    for (Map.Entry<LocalDate, Integer> entry : request.dailyAvailabilityMinutes().entrySet()) {
      if (entry.getKey() == null
          || entry.getKey().isBefore(request.startDate())
          || entry.getKey().isAfter(request.startDate().plusDays(6))
          || entry.getValue() == null
          || entry.getValue() < 0
          || entry.getValue() > 1440) {
        throw new IllegalArgumentException(
            "Availability must be 0-1440 minutes within the template week");
      }
    }
  }

  private void validateItems(
      String authorization, PlanRequest request, LocalDate endDate, List<PlanItem> items) {
    Map<UUID, AssessmentView> known = new HashMap<>();
    tools.getIncompleteAssessments(authorization).forEach(a -> known.put(a.id(), a));
    Map<DayOfWeek, Integer> weeklyAvailability = new EnumMap<>(DayOfWeek.class);
    request
        .dailyAvailabilityMinutes()
        .forEach(
            (date, minutes) -> weeklyAvailability.merge(date.getDayOfWeek(), minutes, Math::max));
    Map<LocalDate, Integer> daily = new HashMap<>();
    for (PlanItem item : items) {
      AssessmentView assessment = known.get(item.assessmentId());
      if (assessment == null)
        throw new IllegalArgumentException(
            "Plan references a missing or completed assessment: " + item.assessmentId());
      if (!assessment.subjectId().equals(item.subjectId())) {
        throw new IllegalArgumentException("Assessment and subject do not match");
      }
      if (item.date().isBefore(request.startDate()) || item.date().isAfter(endDate)) {
        throw new IllegalArgumentException("Plan item lies outside the requested period");
      }
      if (assessment.dueDate() != null && item.date().isAfter(assessment.dueDate())) {
        throw new IllegalArgumentException("Plan item lies after its assessment due date");
      }
      if (item.allocatedMinutes() <= 0)
        throw new IllegalArgumentException("Allocated minutes must be positive");
      int total = daily.merge(item.date(), item.allocatedMinutes(), Integer::sum);
      if (total > weeklyAvailability.getOrDefault(item.date().getDayOfWeek(), 0)) {
        throw new IllegalArgumentException("Plan exceeds availability on " + item.date());
      }
    }
  }

  private String explanation(StudyPlanningAgent.Schedule schedule, String verb) {
    String text =
        verb
            + " a deadline plan through "
            + schedule.endDate()
            + ". Scheduled "
            + schedule.scheduledMinutes()
            + " of "
            + schedule.requestedMinutes()
            + " estimated minutes across spaced study and review sessions. "
            + "Your weekly availability repeats as a template until each due date.";
    if (schedule.unscheduledMinutes() > 0) {
      text +=
          " Add more availability to place the remaining "
              + schedule.unscheduledMinutes()
              + " minutes.";
    }
    return text;
  }

  private String difference(List<PlanItem> before, List<PlanItem> after) {
    Set<UUID> previous = new HashSet<>();
    Set<UUID> current = new HashSet<>();
    before.forEach(item -> previous.add(item.assessmentId()));
    after.forEach(item -> current.add(item.assessmentId()));
    int added = (int) current.stream().filter(id -> !previous.contains(id)).count();
    int removed = (int) previous.stream().filter(id -> !current.contains(id)).count();
    return added
        + " assessment(s) added and "
        + removed
        + " removed; dates and minutes were recalculated";
  }

  private PlanResponse response(StudyPlan plan) {
    try {
      return new PlanResponse(
          plan.getId(),
          plan.getStartDate(),
          plan.getEndDate(),
          plan.getVersion(),
          json.readValue(plan.getItemsJson(), new TypeReference<>() {}),
          plan.getExplanation(),
          plan.getCreatedAt());
    } catch (Exception exception) {
      throw new IllegalStateException("Stored plan is unreadable", exception);
    }
  }
}
