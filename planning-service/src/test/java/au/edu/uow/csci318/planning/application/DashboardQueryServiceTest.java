package au.edu.uow.csci318.planning.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import au.edu.uow.csci318.planning.domain.stream.*;
import au.edu.uow.csci318.planning.infrastructure.StudyPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {
  @Test
  void queriesUseOnlyLocalStateAndRespectTimezoneAndRollingDueDates() {
    UUID owner = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    UUID assessment = UUID.randomUUID();
    Instant now = Instant.parse("2026-09-20T15:00:00Z"); // Monday in Sydney, still Sunday in UTC.
    ProjectionStore store = mock(ProjectionStore.class);
    WorkloadState state =
        WorkloadState.empty()
            .apply(
                new AssessmentChange(
                    owner,
                    1,
                    false,
                    new AssessmentSnapshot(
                        assessment,
                        subject,
                        "Report",
                        "Report",
                        30.0,
                        LocalDate.of(2026, 9, 21),
                        null,
                        null,
                        180,
                        "HIGH",
                        "INCOMPLETE",
                        now),
                    now));
    ProgressState progress =
        ProgressState.empty()
            .apply(
                new ProgressChange(
                    owner,
                    subject,
                    null,
                    1,
                    false,
                    null,
                    new SubjectSnapshot(subject, "CSCI318", "Software Engineering", 120),
                    now));
    progress =
        progress.apply(
            new ProgressChange(
                owner,
                subject,
                "block",
                1,
                false,
                new SessionSnapshot(
                    UUID.randomUUID(), subject, assessment, 60, LocalDate.of(2026, 9, 21), "Notes"),
                null,
                now));
    when(store.workload(owner)).thenReturn(Optional.of(state));
    when(store.progress(owner)).thenReturn(List.of(progress));
    DashboardQueryService queries =
        new DashboardQueryService(
            store,
            mock(StudyPlanRepository.class),
            new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC));
    var sydney = queries.snapshot(owner, ZoneId.of("Australia/Sydney"));
    var utc = queries.snapshot(owner, ZoneOffset.UTC);
    assertEquals(LocalDate.of(2026, 9, 21), sydney.week().weekStart());
    assertEquals(60, sydney.week().studyProgress().getFirst().studiedMinutes());
    assertEquals(0, utc.week().studyProgress().getFirst().studiedMinutes());
    assertEquals(1, sydney.week().workload().incompleteAssessments());
    assertEquals(180, sydney.week().workload().estimatedMinutesDueWithinSevenDays());
    assertEquals(60, queries.completedAssessmentMinutes(owner).get(assessment));
    assertEquals("KAFKA_STREAMS", sydney.stream().source());
    assertTrue(sydney.stream().initialized());
  }
}
