package au.edu.uow.csci318.assessment.application;

import au.edu.uow.csci318.assessment.domain.Assessment;
import au.edu.uow.csci318.assessment.domain.Assessment.Priority;
import au.edu.uow.csci318.assessment.dto.AssessmentDtos.*;
import au.edu.uow.csci318.assessment.infrastructure.AssessmentRepository;
import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.application.SnapshotSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AssessmentApplicationService implements SnapshotSource {
  private final AssessmentRepository repo;
  private final EventPublisher events;
  private final RestClient subjects;

  public AssessmentApplicationService(
      AssessmentRepository repo,
      EventPublisher events,
      RestClient.Builder builder,
      @Value("${services.subject-url}") String url) {
    this.repo = repo;
    this.events = events;
    this.subjects = builder.baseUrl(url).build();
  }

  @Transactional
  public List<Response> importAll(UUID ownerId, String authorization, ImportRequest request) {
    verifySubject(authorization, request.subjectId());
    List<Response> output = new ArrayList<>();
    Set<String> titles = new HashSet<>();
    for (AssessmentCandidate candidate : request.assessments()) {
      String key = candidate.title().trim().toLowerCase();
      if (!titles.add(key))
        throw new IllegalArgumentException("Duplicate assessment: " + candidate.title());
      var existing =
          repo.findByOwnerIdAndSubjectIdAndTitleIgnoreCase(
              ownerId, request.subjectId(), candidate.title());
      if (existing.isPresent()) {
        output.add(response(existing.get()));
        continue;
      }
      Integer minutes =
          candidate.estimatedHours() == null
              ? null
              : (int) Math.round(candidate.estimatedHours() * 60);
      output.add(
          createInternal(
              new Assessment(
                  ownerId,
                  request.subjectId(),
                  candidate.title(),
                  candidate.type(),
                  candidate.weighting(),
                  candidate.dueDate(),
                  candidate.dueWeek(),
                  candidate.description(),
                  minutes,
                  Priority.MEDIUM),
              "AssessmentCreated"));
    }
    return output;
  }

  @Transactional
  public Response create(UUID ownerId, String authorization, CreateRequest request) {
    verifySubject(authorization, request.subjectId());
    if (repo.existsByOwnerIdAndSubjectIdAndTitleIgnoreCase(
        ownerId, request.subjectId(), request.title())) {
      throw new IllegalArgumentException("Duplicate assessment: " + request.title());
    }
    return createInternal(
        new Assessment(
            ownerId,
            request.subjectId(),
            request.title(),
            request.type(),
            request.weighting(),
            request.dueDate(),
            request.dueWeek(),
            request.description(),
            request.estimatedMinutes(),
            request.priority()),
        "AssessmentCreated");
  }

  public List<Response> all(UUID ownerId, String status, UUID subjectId) {
    return repo.findByOwnerIdOrderByDueDateAsc(ownerId).stream()
        .filter(item -> status == null || item.getStatus().name().equalsIgnoreCase(status))
        .filter(item -> subjectId == null || item.getSubjectId().equals(subjectId))
        .map(this::response)
        .toList();
  }

  public Response one(UUID ownerId, UUID id) {
    return response(find(ownerId, id));
  }

  @Transactional
  public Response update(UUID ownerId, UUID id, UpdateRequest request) {
    Assessment assessment = find(ownerId, id);
    repo.findByOwnerIdAndSubjectIdAndTitleIgnoreCase(
            ownerId, assessment.getSubjectId(), request.title())
        .filter(existing -> !existing.getId().equals(id))
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException("Duplicate assessment: " + request.title());
            });
    assessment.updateDetails(
        request.title(),
        request.type(),
        request.weighting(),
        request.dueDate(),
        request.dueWeek(),
        request.description(),
        request.estimatedMinutes(),
        request.priority());
    repo.save(assessment);
    publish("AssessmentUpdated", assessment);
    return response(assessment);
  }

  @Transactional
  public Response complete(UUID ownerId, UUID id) {
    Assessment assessment = find(ownerId, id);
    assessment.markCompleted();
    repo.save(assessment);
    publish("AssessmentCompleted", assessment);
    return response(assessment);
  }

  @Transactional
  public void delete(UUID ownerId, UUID id) {
    Assessment assessment = find(ownerId, id);
    events.publish(
        "assessmentEvents-out-0",
        "AssessmentDeleted",
        ownerId,
        id,
        assessment.getEventRevision() + 1,
        response(assessment));
    repo.delete(assessment);
  }

  private void verifySubject(String authorization, UUID id) {
    try {
      subjects
          .get()
          .uri("/api/subjects/{id}", id)
          .header("Authorization", authorization)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound exception) {
      throw new IllegalArgumentException("Referenced subject does not exist", exception);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is4xxClientError()) {
        throw new IllegalArgumentException(
            "Subject Service rejected subject verification", exception);
      }
      throw new DependencyException("Subject Service is unavailable", exception);
    } catch (ResourceAccessException exception) {
      throw new DependencyException("Subject Service is unavailable", exception);
    }
  }

  private Response createInternal(Assessment assessment, String eventType) {
    repo.save(assessment);
    publish(eventType, assessment);
    return response(assessment);
  }

  private Assessment find(UUID ownerId, UUID id) {
    return repo.findByIdAndOwnerId(id, ownerId)
        .orElseThrow(() -> new NoSuchElementException("Assessment not found"));
  }

  private void publish(String type, Assessment assessment) {
    events.publish(
        "assessmentEvents-out-0",
        type,
        assessment.getOwnerId(),
        assessment.getId(),
        assessment.getEventRevision(),
        response(assessment));
  }

  @Override
  public String migrationKey() {
    return "assessment-owner-events-v2";
  }

  @Override
  public void enqueueSnapshots() {
    repo.findAll().forEach(assessment -> publish("AssessmentSnapshot", assessment));
  }

  private Response response(Assessment assessment) {
    return new Response(
        assessment.getId(),
        assessment.getSubjectId(),
        assessment.getTitle(),
        assessment.getType(),
        assessment.getWeighting(),
        assessment.getDueDate(),
        assessment.getDueWeek(),
        assessment.getDescription(),
        assessment.getEstimatedMinutes(),
        assessment.getPriority(),
        assessment.getStatus(),
        assessment.getUpdatedAt());
  }

  public static class DependencyException extends RuntimeException {
    public DependencyException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
