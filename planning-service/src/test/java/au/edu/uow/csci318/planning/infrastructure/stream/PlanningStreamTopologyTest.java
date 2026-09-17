package au.edu.uow.csci318.planning.infrastructure.stream;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uow.csci318.messaging.contract.IntegrationEvent;
import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PlanningStreamTopologyTest {
  @TempDir Path stateDirectory;
  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
  private final UUID owner = UUID.randomUUID();
  private final UUID subject = UUID.randomUUID();
  private final UUID assessment = UUID.randomUUID();
  private final Instant timestamp = Instant.parse("2026-09-17T09:00:00Z");
  private TopologyTestDriver driver;
  private TestInputTopic<String, String> assessments;
  private TestInputTopic<String, String> sessions;
  private TestInputTopic<String, String> subjects;
  private TestOutputTopic<String, String> rejected;

  @BeforeEach
  void start() {
    StreamsBuilder builder = new StreamsBuilder();
    PlanningStreamTopology topology = new PlanningStreamTopology(new EventDecoder(json), json);
    topology
        .workload(builder.stream("assessments", Consumed.with(Serdes.String(), Serdes.String())))
        .to("workload-output", Produced.with(Serdes.String(), Serdes.String()));
    topology
        .progress(
            builder.stream("sessions", Consumed.with(Serdes.String(), Serdes.String())),
            builder.stream("subjects", Consumed.with(Serdes.String(), Serdes.String())))
        .to("progress-output", Produced.with(Serdes.String(), Serdes.String()));
    Properties properties = new Properties();
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "topology-test");
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
    driver = new TopologyTestDriver(builder.build(), properties);
    assessments = input("assessments");
    sessions = input("sessions");
    subjects = input("subjects");
    rejected =
        driver.createOutputTopic(
            PlanningStreamTopology.REJECTED_TOPIC,
            new StringDeserializer(),
            new StringDeserializer());
  }

  @AfterEach
  void stop() {
    driver.close();
  }

  private TestInputTopic<String, String> input(String name) {
    return driver.createInputTopic(name, new StringSerializer(), new StringSerializer());
  }

  private String event(
      String type, String source, UUID account, UUID id, long revision, Object snapshot)
      throws Exception {
    return json.writeValueAsString(
        new IntegrationEvent(
            UUID.randomUUID(),
            type,
            2,
            timestamp,
            source,
            account,
            id,
            revision,
            json.valueToTree(snapshot)));
  }

  private AssessmentSnapshot assessment(int minutes, String status) {
    return new AssessmentSnapshot(
        assessment,
        subject,
        "Report",
        "Report",
        30.0,
        LocalDate.of(2026, 9, 20),
        null,
        null,
        minutes,
        "HIGH",
        status,
        timestamp);
  }

  private WorkloadState workload(UUID account) {
    return driver
        .<String, WorkloadState>getKeyValueStore(PlanningStreamTopology.WORKLOAD_STORE)
        .get(account.toString());
  }

  private ProgressState progress() {
    return driver
        .<String, ProgressState>getKeyValueStore(PlanningStreamTopology.PROGRESS_STORE)
        .get(owner + "/" + subject);
  }

  @Test
  void assessmentUpdatesReplaceStateAndCompletionAndDeletionRetractWorkload() throws Exception {
    String created =
        event(
            "AssessmentCreated",
            "assessment-service",
            owner,
            assessment,
            1,
            assessment(180, "INCOMPLETE"));
    assessments.pipeInput("original-key", created);
    assessments.pipeInput("original-key", created);
    assessments.pipeInput(
        "original-key",
        event(
            "AssessmentUpdated",
            "assessment-service",
            owner,
            assessment,
            2,
            assessment(90, "INCOMPLETE")));
    assertEquals(1, workload(owner).incompleteCount());
    assertEquals(90, workload(owner).estimatedMinutes());
    assertEquals(2, workload(owner).revision());
    assessments.pipeInput(
        "original-key",
        event(
            "AssessmentCompleted",
            "assessment-service",
            owner,
            assessment,
            3,
            assessment(90, "COMPLETED")));
    assertEquals(0, workload(owner).incompleteCount());
    assertEquals(0, workload(owner).estimatedMinutes());
    assessments.pipeInput("original-key", created); // Stale creation cannot undo completion.
    assertEquals(0, workload(owner).incompleteCount());
    assessments.pipeInput(
        "original-key",
        event(
            "AssessmentDeleted",
            "assessment-service",
            owner,
            assessment,
            4,
            assessment(90, "COMPLETED")));
    assessments.pipeInput("original-key", created);
    assertTrue(workload(owner).assessments().get(assessment).deleted());
  }

  @Test
  void workloadsAreAccountIsolatedEvenWithTheSameEntityKey() throws Exception {
    UUID other = UUID.randomUUID();
    assessments.pipeInput(
        "same-key",
        event(
            "AssessmentCreated",
            "assessment-service",
            owner,
            assessment,
            1,
            assessment(60, "INCOMPLETE")));
    assessments.pipeInput(
        "same-key",
        event(
            "AssessmentCreated",
            "assessment-service",
            other,
            assessment,
            1,
            assessment(120, "INCOMPLETE")));
    assertEquals(60, workload(owner).estimatedMinutes());
    assertEquals(120, workload(other).estimatedMinutes());
  }

  @Test
  void malformedLegacyAndWrongSourceEventsAreQuarantinedWithoutPrivatePayloads() throws Exception {
    assessments.pipeInput("bad", "not JSON private-text");
    assessments.pipeInput(
        "bad",
        event(
            "AssessmentCreated",
            "assessment-service",
            null,
            assessment,
            1,
            assessment(60, "INCOMPLETE")));
    assessments.pipeInput(
        "bad",
        event(
            "AssessmentCreated",
            "study-activity-service",
            owner,
            assessment,
            1,
            assessment(60, "INCOMPLETE")));
    assertNull(workload(owner));
    List<String> metadata = rejected.readValuesToList();
    assertEquals(3, metadata.size());
    assertTrue(
        metadata.stream()
            .noneMatch(body -> body.contains("private-text") || body.contains("Report")));
  }

  @Test
  void studyCorrectionsMoveMinutesBetweenWeeksAndTargetsCanArriveLater() throws Exception {
    UUID id = UUID.randomUUID();
    LocalDate monday = LocalDate.of(2026, 9, 14);
    SessionSnapshot first = new SessionSnapshot(id, subject, null, 45, monday, "Notes");
    String recorded = event("StudySessionRecorded", "study-activity-service", owner, id, 1, first);
    sessions.pipeInput("session-key", recorded);
    sessions.pipeInput("session-key", recorded);
    subjects.pipeInput(
        "subject-key",
        event(
            "SubjectCreated",
            "subject-service",
            owner,
            subject,
            1,
            new SubjectSnapshot(subject, "CSCI318", "Software Engineering", 120)));
    assertEquals(45, progress().totalMinutes());
    assertEquals(45, progress().weeklyMinutes().get(monday));
    assertEquals(120, progress().subject().weeklyStudyTargetMinutes());
    sessions.pipeInput(
        "session-key",
        event(
            "StudySessionUpdated",
            "study-activity-service",
            owner,
            id,
            2,
            new SessionSnapshot(id, subject, null, 30, monday.minusWeeks(1), "Correction")));
    assertEquals(0, progress().weeklyMinutes().getOrDefault(monday, 0));
    assertEquals(30, progress().weeklyMinutes().get(monday.minusWeeks(1)));
    assertEquals(30, progress().totalMinutes());
    subjects.pipeInput(
        "subject-key",
        event(
            "SubjectTargetChanged",
            "subject-service",
            owner,
            subject,
            2,
            new SubjectSnapshot(subject, "CSCI318", "Software Engineering", 240)));
    assertEquals(240, progress().subject().weeklyStudyTargetMinutes());
    sessions.pipeInput(
        "session-key", event("StudySessionDeleted", "study-activity-service", owner, id, 3, first));
    sessions.pipeInput("session-key", recorded);
    assertEquals(0, progress().totalMinutes());
  }

  @Test
  void completedCalendarBlocksContributeOnceAndAreRetractedOnDeletion() throws Exception {
    UUID block = UUID.randomUUID();
    SessionSnapshot snapshot =
        new SessionSnapshot(block, subject, assessment, 90, LocalDate.of(2026, 9, 17), "Revision");
    String completed = event("StudyBlockCompleted", "planning-service", owner, block, 2, snapshot);
    sessions.pipeInput("block", completed);
    sessions.pipeInput("block", completed);
    assertEquals(90, progress().totalMinutes());
    assertEquals(
        assessment,
        progress().sessions().get("planning-service/" + block).snapshot().assessmentId());
    sessions.pipeInput(
        "block", event("StudyBlockDeleted", "planning-service", owner, block, 3, snapshot));
    assertEquals(0, progress().totalMinutes());
  }
}
