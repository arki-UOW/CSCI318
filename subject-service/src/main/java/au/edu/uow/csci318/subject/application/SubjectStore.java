package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.subject.domain.Subject;
import java.util.*;

public interface SubjectStore {
  Optional<Subject> subject(UUID ownerId, UUID id);

  Optional<Subject> byCode(UUID ownerId, String code);

  List<Subject> allSubjects(UUID ownerId);

  List<Subject> snapshots();

  Subject storeSubject(Subject subject);
}
