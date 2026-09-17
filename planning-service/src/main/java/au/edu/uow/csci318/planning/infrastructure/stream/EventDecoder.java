package au.edu.uow.csci318.planning.infrastructure.stream;

import au.edu.uow.csci318.messaging.contract.IntegrationEvent;
import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.*;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Anti-corruption boundary: untrusted transport schemas become validated Planning values. */
@Component
public class EventDecoder {
  private static final Set<String> ASSESSMENT_TYPES =
      Set.of(
          "AssessmentCreated",
          "AssessmentSnapshot",
          "AssessmentUpdated",
          "AssessmentDeadlineChanged",
          "AssessmentWorkloadChanged",
          "AssessmentPriorityChanged",
          "AssessmentCompleted",
          "AssessmentDeleted");
  private static final Set<String> SESSION_TYPES =
      Set.of(
          "StudySessionRecorded",
          "StudySessionSnapshot",
          "StudySessionUpdated",
          "StudySessionDeleted");
  private static final Set<String> SUBJECT_TYPES =
      Set.of("SubjectCreated", "SubjectSnapshot", "SubjectTargetChanged");
  private final ObjectMapper json;

  public EventDecoder(ObjectMapper mapper) {
    json = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public Decoded<AssessmentChange> assessment(String body) {
    try {
      IntegrationEvent event = envelope(body);
      require(
          "assessment-service".equals(event.sourceService())
              && ASSESSMENT_TYPES.contains(event.eventType()));
      AssessmentSnapshot snapshot = json.treeToValue(event.payload(), AssessmentSnapshot.class);
      require(event.aggregateId().equals(snapshot.id()));
      return new Decoded<>(
          new AssessmentChange(
              event.ownerId(),
              event.aggregateRevision(),
              "AssessmentDeleted".equals(event.eventType()),
              snapshot,
              event.eventTimestamp()),
          null);
    } catch (Exception exception) {
      return rejected(exception);
    }
  }

  public Decoded<ProgressChange> progress(String body) {
    try {
      IntegrationEvent event = envelope(body);
      boolean subject =
          "subject-service".equals(event.sourceService())
              && SUBJECT_TYPES.contains(event.eventType());
      boolean session =
          "study-activity-service".equals(event.sourceService())
              && SESSION_TYPES.contains(event.eventType());
      boolean block =
          "planning-service".equals(event.sourceService())
              && Set.of("StudyBlockCompleted", "StudyBlockDeleted").contains(event.eventType());
      require(subject || session || block);
      if (subject) {
        SubjectSnapshot snapshot = json.treeToValue(event.payload(), SubjectSnapshot.class);
        require(event.aggregateId().equals(snapshot.id()));
        return new Decoded<>(
            new ProgressChange(
                event.ownerId(),
                snapshot.id(),
                null,
                event.aggregateRevision(),
                false,
                null,
                snapshot,
                event.eventTimestamp()),
            null);
      }
      SessionSnapshot snapshot = json.treeToValue(event.payload(), SessionSnapshot.class);
      require(event.aggregateId().equals(snapshot.id()));
      return new Decoded<>(
          new ProgressChange(
              event.ownerId(),
              snapshot.subjectId(),
              event.sourceService() + "/" + snapshot.id(),
              event.aggregateRevision(),
              event.eventType().endsWith("Deleted"),
              snapshot,
              null,
              event.eventTimestamp()),
          null);
    } catch (Exception exception) {
      return rejected(exception);
    }
  }

  private IntegrationEvent envelope(String body) throws Exception {
    if (body == null || body.length() > 1_000_000)
      throw new IllegalArgumentException("Invalid event size");
    IntegrationEvent event = json.readValue(body, IntegrationEvent.class);
    require(
        event.eventId() != null
            && event.ownerId() != null
            && event.aggregateId() != null
            && event.eventVersion() == 2
            && event.aggregateRevision() >= 1
            && event.eventTimestamp() != null
            && event.payload() != null);
    return event;
  }

  private static void require(boolean valid) {
    if (!valid) throw new IllegalArgumentException("Unsupported schema, source, type or identity");
  }

  private static <T> Decoded<T> rejected(Exception exception) {
    // Quarantine metadata only: do not broadcast private payloads or parser exception text.
    return new Decoded<>(
        null,
        "{\"reason\":\"Invalid or unsupported v2 event\",\"category\":\""
            + exception.getClass().getSimpleName()
            + "\"}");
  }

  public record Decoded<T>(T change, String rejection) {
    public boolean valid() {
      return change != null;
    }
  }
}
