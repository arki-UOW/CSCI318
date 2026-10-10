package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import au.edu.uow.csci318.planning.domain.WeeklyTimeSlots;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningApplicationService {
  private final StudyPlanRepository plans;
  private final StudyPlanningAgent agent;
  private final AgenticPlanningAdvisor advisor;
  private final PlanningTools tools;
  private final AvailabilityAssistant availabilityAssistant;
  private final ConfiguredPlanningChatModel configuredModel;
  private final CalendarApplicationService calendar;
  private final ObjectMapper json;

  public PlanningApplicationService(
      StudyPlanRepository plans,
      StudyPlanningAgent agent,
      AgenticPlanningAdvisor advisor,
      PlanningTools tools,
      AvailabilityAssistant availabilityAssistant,
      ConfiguredPlanningChatModel configuredModel,
      CalendarApplicationService calendar,
      ObjectMapper json) {
    this.plans = plans;
    this.agent = agent;
    this.advisor = advisor;
    this.tools = tools;
    this.availabilityAssistant = availabilityAssistant;
    this.configuredModel = configuredModel;
    this.calendar = calendar;
    this.json = json;
  }

  @Transactional
  public PlanResponse generate(
      UUID ownerId, String authorization, ZoneId timezone, PlanRequest request) {
    WeeklyTimeSlots slots = validatePeriod(request);
    StudyPlanningAgent.Schedule candidate = agent.generate(ownerId, request, authorization);
    AgenticPlanningAdvisor.Advice advice =
        advisor.adviseGenerate(ownerId, authorization, timezone, request, candidate);
    StudyPlanningAgent.Schedule schedule =
        submittedSchedule(ownerId, authorization, request, candidate, advice);
    try {
      int version =
          plans
              .findTopByOwnerIdOrderByVersionDesc(ownerId)
              .map(existing -> existing.getVersion() + 1)
              .orElse(1);
      String explanation = explanation(schedule, "Generated", advice);
      StudyPlan saved =
          plans.save(
              new StudyPlan(
                  ownerId,
                  request.startDate(),
                  schedule.endDate(),
                  version,
                  json.writeValueAsString(schedule.items()),
                  explanation));
      calendar.replaceAiPlan(ownerId, saved.getId(), schedule.items(), slots);
      return response(saved);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Plan could not be stored", exception);
    }
  }

  @Transactional
  public PlanResponse regenerate(
      UUID ownerId, String authorization, ZoneId timezone, UUID previousId, PlanRequest request) {
    StudyPlan old =
        plans
            .findByIdAndOwnerId(previousId, ownerId)
            .orElseThrow(() -> new NoSuchElementException("Study plan not found"));
    WeeklyTimeSlots slots = validatePeriod(request);
    StudyPlanningAgent.Schedule candidate = agent.generate(ownerId, request, authorization);
    AgenticPlanningAdvisor.Advice advice =
        advisor.adviseRegenerate(ownerId, authorization, timezone, request, previousId, candidate);
    StudyPlanningAgent.Schedule schedule =
        submittedSchedule(ownerId, authorization, request, candidate, advice);
    try {
      List<PlanItem> before = json.readValue(old.getItemsJson(), new TypeReference<>() {});
      String explanation =
          explanation(schedule, "Regenerated", advice)
              + " "
              + difference(before, schedule.items())
              + ".";
      StudyPlan saved =
          plans.save(
              new StudyPlan(
                  ownerId,
                  request.startDate(),
                  schedule.endDate(),
                  plans
                      .findTopByOwnerIdOrderByVersionDesc(ownerId)
                      .map(p -> p.getVersion() + 1)
                      .orElse(1),
                  json.writeValueAsString(schedule.items()),
                  explanation));
      calendar.replaceAiPlan(ownerId, saved.getId(), schedule.items(), slots);
      return response(saved);
    } catch (IllegalArgumentException exception) {
      throw exception;
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

  public List<PlanResponse> history(UUID ownerId) {
    return plans.findByOwnerIdOrderByVersionDesc(ownerId).stream().map(this::response).toList();
  }

  public PlanResponse get(UUID ownerId, UUID id) {
    return response(
        plans
            .findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new NoSuchElementException("Study plan not found")));
  }

  private StudyPlanningAgent.Schedule submittedSchedule(
      UUID ownerId,
      String authorization,
      PlanRequest request,
      StudyPlanningAgent.Schedule candidate,
      AgenticPlanningAdvisor.Advice advice) {
    List<PlanItem> items = advice.items();
    validateItems(ownerId, authorization, request, request.startDate().plusDays(6), items);
    int scheduled = items.stream().mapToInt(PlanItem::allocatedMinutes).sum();
    if (scheduled < candidate.scheduledMinutes())
      throw new IllegalArgumentException(
          "The submitted plan leaves feasible study time unused. Please retry generation.");
    return new StudyPlanningAgent.Schedule(
        List.copyOf(items),
        request.startDate().plusDays(6),
        candidate.requestedMinutes(),
        scheduled);
  }

  private WeeklyTimeSlots validatePeriod(PlanRequest request) {
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
    if (request.availabilitySlots() == null) return null;
    Map<LocalDate, List<WeeklyTimeSlots.Window>> windows = new HashMap<>();
    request
        .availabilitySlots()
        .forEach(
            (date, values) -> {
              if (values == null || values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Availability time slots cannot be null");
              }
              windows.put(
                  date,
                  values.stream()
                      .map(value -> new WeeklyTimeSlots.Window(value.start(), value.end()))
                      .toList());
            });
    WeeklyTimeSlots slots = WeeklyTimeSlots.from(request.startDate(), windows);
    for (int day = 0; day < 7; day++) {
      LocalDate date = request.startDate().plusDays(day);
      if (slots.minutes(date.getDayOfWeek())
          != request.dailyAvailabilityMinutes().getOrDefault(date, 0)) {
        throw new IllegalArgumentException(
            "Daily minutes must match the supplied availability slots");
      }
    }
    return slots;
  }

  private void validateItems(
      UUID ownerId,
      String authorization,
      PlanRequest request,
      LocalDate endDate,
      List<PlanItem> items) {
    if (items == null || items.size() > 1000)
      throw new IllegalArgumentException("Plan items are required and limited to 1000 blocks");
    Map<UUID, Integer> completed = agent.completedMinutes(ownerId);
    Map<UUID, Integer> allocated = new HashMap<>();
    Map<UUID, AssessmentView> known = new HashMap<>();
    tools.getIncompleteAssessments(authorization).forEach(a -> known.put(a.id(), a));
    Map<DayOfWeek, Integer> weeklyAvailability = new EnumMap<>(DayOfWeek.class);
    request
        .dailyAvailabilityMinutes()
        .forEach(
            (date, minutes) -> weeklyAvailability.merge(date.getDayOfWeek(), minutes, Math::max));
    Map<LocalDate, Integer> daily = new HashMap<>();
    for (PlanItem item : items) {
      if (item == null
          || item.date() == null
          || item.subjectId() == null
          || item.assessmentId() == null
          || item.title() == null
          || item.title().isBlank()
          || item.title().length() > 300
          || item.repetitionStage() < 0
          || item.repetitionStage() > 1000)
        throw new IllegalArgumentException("Plan contains missing or invalid fields");
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
      if (item.allocatedMinutes() > 1440)
        throw new IllegalArgumentException("A study block cannot exceed 1440 minutes");
      int remaining =
          Math.max(
              0,
              (assessment.estimatedMinutes() == null ? 120 : assessment.estimatedMinutes())
                  - completed.getOrDefault(assessment.id(), 0));
      if (allocated.merge(assessment.id(), item.allocatedMinutes(), Integer::sum) > remaining)
        throw new IllegalArgumentException("Plan exceeds remaining assessment workload");
      int total = daily.merge(item.date(), item.allocatedMinutes(), Integer::sum);
      if (total > weeklyAvailability.getOrDefault(item.date().getDayOfWeek(), 0)) {
        throw new IllegalArgumentException("Plan exceeds availability on " + item.date());
      }
    }
  }

  private String explanation(
      StudyPlanningAgent.Schedule schedule, String verb, AgenticPlanningAdvisor.Advice advice) {
    String text =
        advice.provider()
            + " agent used "
            + String.join(", ", advice.toolsUsed())
            + ". "
            + advice.summary()
            + " "
            + verb
            + " a seven-day plan through "
            + schedule.endDate()
            + ". Scheduled "
            + schedule.scheduledMinutes()
            + " of "
            + schedule.requestedMinutes()
            + " estimated minutes across spaced study and review sessions. "
            + "Only the requested seven days are scheduled.";
    if (schedule.unscheduledMinutes() > 0) {
      text +=
          " Remaining estimated work beyond this plan: "
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
