package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.dto.PlanningDtos.AssessmentView;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanItem;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StudyPlanningAgent {
    private final PlanningTools tools;
    private final ObjectMapper json;
    private final ConfiguredPlanningChatModel configuredModel;

    public StudyPlanningAgent(
            PlanningTools tools,
            ObjectMapper json,
            ConfiguredPlanningChatModel configuredModel) {
        this.tools = tools;
        this.json = json;
        this.configuredModel = configuredModel;
    }

    public List<PlanItem> generate(PlanRequest request) {
        List<AssessmentView> work = tools.getIncompleteAssessments();
        var selection = configuredModel.selection();
        if (selection.isEmpty()) {
            return deterministic(request, work);
        }
        try {
            return ai(request, work, selection.get());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    selection.get().provider()
                            + " could not generate a valid study plan. Check the API key/model and try again.",
                    exception);
        }
    }

    private List<PlanItem> deterministic(PlanRequest request, List<AssessmentView> work) {
        List<AssessmentView> sorted = work.stream()
                .filter(assessment -> assessment.dueDate() == null
                        || !assessment.dueDate().isBefore(request.startDate()))
                .sorted(Comparator
                        .comparing((AssessmentView assessment) -> assessment.dueDate() == null
                                ? LocalDate.MAX
                                : assessment.dueDate())
                        .thenComparing(assessment -> "HIGH".equals(assessment.priority()) ? 0 : 1)
                        .thenComparingDouble(assessment -> assessment.weighting() == null
                                ? 0
                                : -assessment.weighting()))
                .toList();
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
                items.add(new PlanItem(
                        date,
                        assessment.subjectId(),
                        assessment.id(),
                        assessment.title(),
                        minutes));
                remaining.put(assessment.id(), left - minutes);
                available -= minutes;
            }
        }
        return items;
    }

    private List<PlanItem> ai(
            PlanRequest request,
            List<AssessmentView> work,
            ConfiguredPlanningChatModel.Selection selection) throws Exception {
        String prompt = """
                You are a goal-oriented study-planning agent. The assessments below came from the approved
                getIncompleteAssessments tool. Return ONLY a JSON array of objects with date, subjectId,
                assessmentId, title, allocatedMinutes.

                Rules:
                - Use only the supplied assessment and subject IDs.
                - Schedule no completed or missing work.
                - Every date must be inside the seven-day period.
                - allocatedMinutes must be positive.
                - The total on each date must not exceed that date's availability.
                - Prioritise nearer deadlines, higher priority, and higher weighting while keeping the plan practical.
                """
                + "\nPeriod: " + request.startDate() + " to " + request.startDate().plusDays(6)
                + "\nAvailability: " + json.writeValueAsString(request.dailyAvailabilityMinutes())
                + "\nAssessments: " + json.writeValueAsString(work);
        String raw = selection.model().chat(prompt)
                .replaceFirst("(?s)^```(?:json)?\\s*", "")
                .replaceFirst("(?s)\\s*```$", "");
        List<PlanItem> items = json.readValue(raw, new TypeReference<>() {
        });
        if (items == null) {
            throw new IllegalArgumentException("AI returned no plan items");
        }
        return items;
    }
}
