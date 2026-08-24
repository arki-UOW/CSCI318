package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanItem;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class StudyPlanningAgent {
    private final PlanningTools tools;
    private final ObjectMapper json;
    private final ConfiguredPlanningChatModel configuredModel;

    public StudyPlanningAgent(PlanningTools tools, ObjectMapper json,
                              ConfiguredPlanningChatModel configuredModel) {
        this.tools = tools;
        this.json = json;
        this.configuredModel = configuredModel;
    }

    public List<PlanItem> generate(PlanRequest request) {
        List<AssessmentView> work = tools.getIncompleteAssessments();
        if (work.isEmpty()) {
            throw new IllegalArgumentException(
                    "No usable incomplete assessments were found. Remove incorrect rows or add an assessment first.");
        }
        var selection = configuredModel.selection();
        if (selection.isEmpty()) {
            return deterministic(request, work);
        }
        try {
            List<PlanItem> items = sanitise(ai(request, work, selection.get()), request, work);
            return items.isEmpty() ? deterministic(request, work) : items;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    ConfiguredPlanningChatModel.failureMessage(selection.get(), exception, "generate a study plan"),
                    exception);
        }
    }

    public String providerLabel() {
        return configuredModel.selection().map(ConfiguredPlanningChatModel.Selection::provider)
                .orElse("Local scheduler");
    }

    private List<PlanItem> deterministic(PlanRequest request, List<AssessmentView> work) {
        List<AssessmentView> sorted = prioritised(work, request.startDate());
        List<PlanItem> items = new ArrayList<>();
        Map<UUID, Integer> remaining = new HashMap<>();
        sorted.forEach(assessment -> remaining.put(
                assessment.id(), assessment.estimatedMinutes() == null ? 120 : assessment.estimatedMinutes()));
        for (int day = 0; day < 7; day++) {
            LocalDate date = request.startDate().plusDays(day);
            int available = request.dailyAvailabilityMinutes().getOrDefault(date, 0);
            for (AssessmentView assessment : sorted) {
                if (available <= 0) {
                    break;
                }
                if (assessment.dueDate() != null && date.isAfter(assessment.dueDate())) {
                    continue;
                }
                int left = remaining.get(assessment.id());
                if (left <= 0) {
                    continue;
                }
                int minutes = Math.min(Math.min(available, left), 90);
                items.add(new PlanItem(date, assessment.subjectId(), assessment.id(), assessment.title(), minutes));
                remaining.put(assessment.id(), left - minutes);
                available -= minutes;
            }
        }
        return items;
    }

    private List<AssessmentView> prioritised(List<AssessmentView> work, LocalDate startDate) {
        return work.stream()
                .filter(assessment -> assessment.dueDate() == null || !assessment.dueDate().isBefore(startDate))
                .sorted(Comparator
                        .comparing((AssessmentView assessment) -> assessment.dueDate() == null
                                ? LocalDate.MAX : assessment.dueDate())
                        .thenComparing(assessment -> "HIGH".equals(assessment.priority()) ? 0 : 1)
                        .thenComparingDouble(assessment -> assessment.weighting() == null
                                ? 0 : -assessment.weighting()))
                .toList();
    }

    private List<PlanItem> ai(PlanRequest request, List<AssessmentView> work,
                              ConfiguredPlanningChatModel.Selection selection) throws Exception {
        String prompt = """
                You are a practical study-planning agent. Return ONLY JSON containing either an array or an
                object with an items array. Every item has date, subjectId, assessmentId, title and allocatedMinutes.

                Rules:
                - Use only supplied assessment and subject IDs and copy their title exactly.
                - Every date is inside the seven-day period and no later than its due date.
                - Daily allocated minutes never exceed that date's availability.
                - Use focused blocks of 25 to 90 minutes, except a smaller final remainder is allowed.
                - Prioritise nearer deadlines, high priority and higher weighting.
                - Spread substantial work across multiple days and leave zero-availability days empty.
                """
                + "\nPeriod: " + request.startDate() + " to " + request.startDate().plusDays(6)
                + "\nAvailability: " + json.writeValueAsString(request.dailyAvailabilityMinutes())
                + "\nApproved incomplete assessments: " + json.writeValueAsString(work);
        String raw = selection.model().chat(prompt)
                .replaceFirst("(?s)^```(?:json)?\\s*", "")
                .replaceFirst("(?s)\\s*```$", "");
        JsonNode root = json.readTree(raw);
        JsonNode itemsNode = root.isArray() ? root : root.path("items");
        if (!itemsNode.isArray()) {
            throw new IllegalArgumentException("AI response did not contain plan items");
        }
        return json.convertValue(itemsNode, new TypeReference<>() {
        });
    }

    private List<PlanItem> sanitise(List<PlanItem> proposed, PlanRequest request, List<AssessmentView> work) {
        Map<UUID, AssessmentView> known = new HashMap<>();
        work.forEach(assessment -> known.put(assessment.id(), assessment));
        Map<LocalDate, Integer> used = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        List<PlanItem> safe = new ArrayList<>();
        for (PlanItem item : proposed == null ? List.<PlanItem>of() : proposed) {
            if (item == null || item.date() == null || item.assessmentId() == null) {
                continue;
            }
            AssessmentView assessment = known.get(item.assessmentId());
            if (assessment == null || item.date().isBefore(request.startDate())
                    || item.date().isAfter(request.startDate().plusDays(6))
                    || assessment.dueDate() != null && item.date().isAfter(assessment.dueDate())) {
                continue;
            }
            int available = request.dailyAvailabilityMinutes().getOrDefault(item.date(), 0);
            int remainingToday = Math.max(0, available - used.getOrDefault(item.date(), 0));
            int minutes = Math.min(Math.min(Math.max(item.allocatedMinutes(), 0), 180), remainingToday);
            String duplicateKey = item.date() + ":" + item.assessmentId();
            if (minutes <= 0 || !duplicates.add(duplicateKey)) {
                continue;
            }
            used.merge(item.date(), minutes, Integer::sum);
            safe.add(new PlanItem(item.date(), assessment.subjectId(), assessment.id(), assessment.title(), minutes));
        }
        return safe.stream().sorted(Comparator.comparing(PlanItem::date)).toList();
    }
}
