package au.edu.uow.csci318.planning.application;

import java.util.UUID;

/** Verifies account ownership and consistency of optional academic calendar references. */
public interface CalendarReferences {
  void verify(String authorization, UUID subjectId, UUID assessmentId);
}
