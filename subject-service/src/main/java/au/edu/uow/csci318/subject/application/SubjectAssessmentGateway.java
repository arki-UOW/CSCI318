package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.subject.dto.SubjectDtos.AssessmentCandidate;
import java.util.*;

public interface SubjectAssessmentGateway {
  void importAssessments(
      String authorization, UUID subjectId, List<AssessmentCandidate> candidates);
}
