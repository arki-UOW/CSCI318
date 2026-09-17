package au.edu.uow.csci318.planning.infrastructure.projection;

import au.edu.uow.csci318.planning.application.ProjectionStore;
import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaProjectionStore implements ProjectionStore {
  private final ProjectionDocumentRepository documents;
  private final ObjectMapper json;

  public JpaProjectionStore(ProjectionDocumentRepository documents, ObjectMapper json) {
    this.documents = documents;
    this.json = json;
  }

  @Override
  public Optional<WorkloadState> workload(UUID ownerId) {
    return documents
        .findById("workload/" + ownerId)
        .map(document -> read(document.getBody(), WorkloadState.class));
  }

  @Override
  public List<ProgressState> progress(UUID ownerId) {
    return documents.findByOwnerIdAndKind(ownerId, "progress").stream()
        .map(document -> read(document.getBody(), ProgressState.class))
        .toList();
  }

  @Override
  @Transactional
  public boolean saveWorkload(WorkloadState state) {
    return save(
        "workload/" + state.ownerId(), state.ownerId(), "workload", state.revision(), state);
  }

  @Override
  @Transactional
  public boolean saveProgress(ProgressState state) {
    return save(
        "progress/" + state.ownerId() + "/" + state.subjectId(),
        state.ownerId(),
        "progress",
        state.revision(),
        state);
  }

  private boolean save(String id, UUID owner, String kind, long revision, Object state) {
    ProjectionDocument document =
        documents.findById(id).orElseGet(() -> new ProjectionDocument(id, owner, kind));
    if (!document.replace(revision, write(state))) return false;
    documents.save(document);
    return true;
  }

  private <T> T read(String body, Class<T> type) {
    try {
      return json.readValue(body, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored projection is unreadable", exception);
    }
  }

  private String write(Object state) {
    try {
      return json.writeValueAsString(state);
    } catch (Exception exception) {
      throw new IllegalStateException("Projection could not be stored", exception);
    }
  }
}
