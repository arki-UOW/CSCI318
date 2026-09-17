package au.edu.uow.csci318.activity.application;

import au.edu.uow.csci318.activity.domain.StudySession;
import au.edu.uow.csci318.activity.infrastructure.StudySessionRepository;
import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.application.SnapshotSource;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class StudyActivityApplicationService implements SnapshotSource {
  private final StudySessionRepository repo;
  private final EventPublisher events;
  private final RestClient subjects;

  public StudyActivityApplicationService(
      StudySessionRepository repo,
      EventPublisher events,
      RestClient.Builder builder,
      @Value("${services.subject-url}") String url) {
    this.repo = repo;
    this.events = events;
    this.subjects = builder.baseUrl(url).build();
  }

  @Transactional
  public Response record(UUID ownerId, String authorization, CreateRequest request) {
    verify(authorization, request.subjectId());
    StudySession session =
        repo.save(
            new StudySession(
                ownerId,
                request.subjectId(),
                request.durationMinutes(),
                request.studyDate(),
                request.description()));
    publish("StudySessionRecorded", session);
    return response(session);
  }

  @Transactional
  public Response update(UUID ownerId, UUID id, UpdateRequest request) {
    StudySession session = find(ownerId, id);
    session.edit(request.durationMinutes(), request.studyDate(), request.description());
    repo.save(session);
    publish("StudySessionUpdated", session);
    return response(session);
  }

  @Transactional
  public void delete(UUID ownerId, UUID id) {
    StudySession session = find(ownerId, id);
    events.publish(
        "studyActivityEvents-out-0",
        "StudySessionDeleted",
        ownerId,
        id,
        session.getEventRevision() + 1,
        response(session));
    repo.delete(session);
  }

  public List<Response> all(UUID ownerId) {
    return repo.findByOwnerIdOrderByStudyDateDescRecordedAtDesc(ownerId).stream()
        .map(this::response)
        .toList();
  }

  public Summary summary(UUID ownerId, UUID subjectId, LocalDate weekOf) {
    LocalDate from =
        (weekOf == null ? LocalDate.now() : weekOf)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate to = from.plusDays(6);
    var sessions = repo.findByOwnerIdAndSubjectIdAndStudyDateBetween(ownerId, subjectId, from, to);
    return new Summary(
        subjectId,
        from,
        to,
        sessions.stream().mapToInt(StudySession::getDurationMinutes).sum(),
        sessions.size());
  }

  private StudySession find(UUID ownerId, UUID id) {
    return repo.findByIdAndOwnerId(id, ownerId)
        .orElseThrow(() -> new NoSuchElementException("Study session not found"));
  }

  private void verify(String authorization, UUID id) {
    try {
      subjects
          .get()
          .uri("/api/subjects/{id}", id)
          .header("Authorization", authorization)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      throw new DependencyException(
          "Referenced subject does not exist or Subject Service is unavailable", exception);
    }
  }

  private void publish(String type, StudySession session) {
    events.publish(
        "studyActivityEvents-out-0",
        type,
        session.getOwnerId(),
        session.getId(),
        session.getEventRevision(),
        response(session));
  }

  @Override
  public String migrationKey() {
    return "activity-owner-events-v2";
  }

  @Override
  public void enqueueSnapshots() {
    repo.findAll().forEach(session -> publish("StudySessionSnapshot", session));
  }

  private Response response(StudySession session) {
    return new Response(
        session.getId(),
        session.getSubjectId(),
        session.getDurationMinutes(),
        session.getStudyDate(),
        session.getDescription(),
        session.getRecordedAt());
  }

  public record CreateRequest(
      UUID subjectId, int durationMinutes, LocalDate studyDate, String description) {}

  public record UpdateRequest(int durationMinutes, LocalDate studyDate, String description) {}

  public record Response(
      UUID id,
      UUID subjectId,
      int durationMinutes,
      LocalDate studyDate,
      String description,
      Instant recordedAt) {}

  public record Summary(
      UUID subjectId, LocalDate weekStart, LocalDate weekEnd, int totalMinutes, int sessionCount) {}

  public static class DependencyException extends RuntimeException {
    public DependencyException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
