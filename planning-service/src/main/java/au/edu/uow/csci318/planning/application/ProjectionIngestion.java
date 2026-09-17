package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionIngestion {
  private final ProjectionStore store;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  public ProjectionIngestion(
      ProjectionStore store, ObjectMapper json, ApplicationEventPublisher events) {
    this.store = store;
    this.json = json;
    this.events = events;
  }

  @Transactional
  public void workload(String body) {
    WorkloadState state = read(body, WorkloadState.class);
    if (state.ownerId() == null) throw new IllegalArgumentException("Projection owner is required");
    if (store.saveWorkload(state)) events.publishEvent(new ProjectionChanged(state.ownerId()));
  }

  @Transactional
  public void progress(String body) {
    ProgressState state = read(body, ProgressState.class);
    if (state.ownerId() == null || state.subjectId() == null)
      throw new IllegalArgumentException("Projection identity is required");
    if (store.saveProgress(state)) events.publishEvent(new ProjectionChanged(state.ownerId()));
  }

  private <T> T read(String body, Class<T> type) {
    try {
      return json.readValue(body, type);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid projection message", exception);
    }
  }
}
