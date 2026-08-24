package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class PlanningApplicationService {
    private final StudyPlanRepository plans;
    private final StudyPlanningAgent agent;
    private final PlanningTools tools;
    private final AvailabilityAssistant availabilityAssistant;
    private final ConfiguredPlanningChatModel configuredModel;
    private final ObjectMapper json;

    public PlanningApplicationService(StudyPlanRepository plans, StudyPlanningAgent agent,
                                      PlanningTools tools, AvailabilityAssistant availabilityAssistant,
                                      ConfiguredPlanningChatModel configuredModel, ObjectMapper json) {
        this.plans = plans;
        this.agent = agent;
        this.tools = tools;
        this.availabilityAssistant = availabilityAssistant;
        this.configuredModel = configuredModel;
        this.json = json;
    }

    @Transactional
    public PlanResponse generate(PlanRequest request) {
        validatePeriod(request);
        List<PlanItem> items = agent.generate(request);
        validateItems(request, items);
        try {
            int version = plans.findTopByOrderByCreatedAtDesc()
                    .map(existing -> existing.getVersion() + 1).orElse(1);
            String explanation = "Generated with " + agent.providerLabel()
                    + " from approved incomplete assessments and the supplied availability.";
            return response(plans.save(new StudyPlan(request.startDate(), request.startDate().plusDays(6),
                    version, json.writeValueAsString(items), explanation)));
        } catch (Exception exception) {
            throw new IllegalStateException("Plan could not be stored", exception);
        }
    }

    @Transactional
    public PlanResponse regenerate(UUID previousId, PlanRequest request) {
        StudyPlan old = plans.findById(previousId)
                .orElseThrow(() -> new NoSuchElementException("Study plan not found"));
        validatePeriod(request);
        List<PlanItem> items = agent.generate(request);
        validateItems(request, items);
        try {
            List<PlanItem> before = json.readValue(old.getItemsJson(), new TypeReference<>() {
            });
            String explanation = "Regenerated with " + agent.providerLabel()
                    + " after live academic data was re-read: " + difference(before, items) + ".";
            return response(plans.save(new StudyPlan(request.startDate(), request.startDate().plusDays(6),
                    old.getVersion() + 1, json.writeValueAsString(items), explanation)));
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

    public Optional<PlanResponse> latest() {
        return plans.findTopByOrderByCreatedAtDesc().map(this::response);
    }

    public WorkloadSummary workload() {
        List<AssessmentView> assessments = currentAssessments();
        LocalDate now = LocalDate.now();
        LocalDate weekEnd = now.with(DayOfWeek.SUNDAY);
        int incomplete = (int) assessments.stream().filter(a -> "INCOMPLETE".equals(a.status())).count();
        int dueWeek = (int) assessments.stream().filter(a -> "INCOMPLETE".equals(a.status())
                && between(a.dueDate(), now, weekEnd)).count();
        int dueSevenDays = (int) assessments.stream().filter(a -> "INCOMPLETE".equals(a.status())
                && between(a.dueDate(), now, now.plusDays(7))).count();
        int minutes = assessments.stream().filter(a -> "INCOMPLETE".equals(a.status()))
                .map(AssessmentView::estimatedMinutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        int high = (int) assessments.stream().filter(a -> "INCOMPLETE".equals(a.status())
                && "HIGH".equals(a.priority())).count();
        String state = dueSevenDays >= 3 || minutes > 1200 ? "HIGH"
                : dueSevenDays > 0 || minutes > 480 ? "MEDIUM" : "LOW";
        return new WorkloadSummary(incomplete, dueWeek, dueSevenDays, minutes, high, state);
    }

    public ThisWeek thisWeek() {
        LocalDate from = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);
        List<AssessmentView> all = currentAssessments();
        List<AssessmentView> due = all.stream()
                .filter(a -> "INCOMPLETE".equals(a.status()) && between(a.dueDate(), from, to))
                .sorted(Comparator.comparing(AssessmentView::dueDate)).toList();
        List<AssessmentView> upcoming = all.stream()
                .filter(a -> "INCOMPLETE".equals(a.status()) && a.dueDate() != null && a.dueDate().isAfter(to))
                .sorted(Comparator.comparing(AssessmentView::dueDate)).limit(5).toList();
        List<StudyProgress> progress;
        try {
            progress = tools.getSubjects().stream().map(subject -> {
                int done;
                try {
                    done = tools.getStudiedMinutes(subject.id(), from);
                } catch (Exception ignored) {
                    done = 0;
                }
                int target = subject.weeklyStudyTargetMinutes();
                int remaining = Math.max(0, target - done);
                String state = done == 0 ? "NO_ACTIVITY" : done >= target ? "TARGET_REACHED"
                        : done * 2 >= target ? "ON_TRACK" : "BEHIND_TARGET";
                return new StudyProgress(subject.id(), done, target, remaining, state);
            }).toList();
        } catch (Exception ignored) {
            progress = List.of();
        }
        List<PlanItem> items = latest().map(PlanResponse::items).orElse(List.of()).stream()
                .filter(item -> !item.date().isBefore(from) && !item.date().isAfter(to)).toList();
        return new ThisWeek(from, to, due, upcoming, workload(), progress, items);
    }

    private List<AssessmentView> currentAssessments() {
        try {
            return tools.getIncompleteAssessments();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void validatePeriod(PlanRequest request) {
        if (request.startDate() == null || request.dailyAvailabilityMinutes() == null) {
            throw new IllegalArgumentException("Start date and daily availability are required");
        }
        for (Map.Entry<LocalDate, Integer> entry : request.dailyAvailabilityMinutes().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBefore(request.startDate())
                    || entry.getKey().isAfter(request.startDate().plusDays(6))
                    || entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "Availability must be non-negative and within the seven-day period");
            }
        }
    }

    private void validateItems(PlanRequest request, List<PlanItem> items) {
        Map<UUID, AssessmentView> known = new HashMap<>();
        tools.getIncompleteAssessments().forEach(assessment -> known.put(assessment.id(), assessment));
        Map<LocalDate, Integer> daily = new HashMap<>();
        for (PlanItem item : items) {
            AssessmentView assessment = known.get(item.assessmentId());
            if (assessment == null) {
                throw new IllegalArgumentException(
                        "Plan references a missing or completed assessment: " + item.assessmentId());
            }
            if (!assessment.subjectId().equals(item.subjectId())) {
                throw new IllegalArgumentException("Assessment and subject do not match");
            }
            if (item.date().isBefore(request.startDate())
                    || item.date().isAfter(request.startDate().plusDays(6))) {
                throw new IllegalArgumentException("Plan item lies outside the requested period");
            }
            if (item.allocatedMinutes() <= 0) {
                throw new IllegalArgumentException("Allocated minutes must be positive");
            }
            int total = daily.merge(item.date(), item.allocatedMinutes(), Integer::sum);
            if (total > request.dailyAvailabilityMinutes().getOrDefault(item.date(), 0)) {
                throw new IllegalArgumentException("Plan exceeds availability on " + item.date());
            }
        }
    }

    private boolean between(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private String difference(List<PlanItem> before, List<PlanItem> after) {
        Set<UUID> previous = new HashSet<>();
        Set<UUID> current = new HashSet<>();
        before.forEach(item -> previous.add(item.assessmentId()));
        after.forEach(item -> current.add(item.assessmentId()));
        int added = (int) current.stream().filter(id -> !previous.contains(id)).count();
        int removed = (int) previous.stream().filter(id -> !current.contains(id)).count();
        return added + " assessment(s) added and " + removed
                + " removed; dates and minutes were recalculated";
    }

    private PlanResponse response(StudyPlan plan) {
        try {
            return new PlanResponse(plan.getId(), plan.getStartDate(), plan.getEndDate(), plan.getVersion(),
                    json.readValue(plan.getItemsJson(), new TypeReference<>() {
                    }), plan.getExplanation(), plan.getCreatedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored plan is unreadable", exception);
        }
    }
}
