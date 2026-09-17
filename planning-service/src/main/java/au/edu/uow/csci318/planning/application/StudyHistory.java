package au.edu.uow.csci318.planning.application;

import java.util.Map;
import java.util.UUID;

public interface StudyHistory {
  Map<UUID, Integer> completedAssessmentMinutes(UUID ownerId);
}
