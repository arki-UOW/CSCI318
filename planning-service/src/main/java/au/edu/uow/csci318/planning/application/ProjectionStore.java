package au.edu.uow.csci318.planning.application;

import au.edu.uow.csci318.planning.domain.stream.*;
import java.util.*;

/** Application-owned persistence port, implemented by infrastructure. */
public interface ProjectionStore {
  Optional<WorkloadState> workload(UUID ownerId);

  List<ProgressState> progress(UUID ownerId);

  boolean saveWorkload(WorkloadState state);

  boolean saveProgress(ProgressState state);
}
