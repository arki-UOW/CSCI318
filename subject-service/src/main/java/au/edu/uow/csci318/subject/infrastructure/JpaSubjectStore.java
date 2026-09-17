package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.application.SubjectStore;
import au.edu.uow.csci318.subject.domain.Subject;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
class JpaSubjectStore implements SubjectStore {
  private final InternalSubjectRepository repository;

  JpaSubjectStore(InternalSubjectRepository repository) {
    this.repository = repository;
  }

  public Optional<Subject> subject(UUID owner, UUID id) {
    return repository.findByIdAndOwnerId(id, owner);
  }

  public Optional<Subject> byCode(UUID owner, String code) {
    return repository.findByOwnerIdAndCode(owner, code);
  }

  public List<Subject> allSubjects(UUID owner) {
    return repository.findByOwnerIdOrderByCode(owner);
  }

  public List<Subject> snapshots() {
    return repository.findAll();
  }

  public Subject storeSubject(Subject subject) {
    return repository.save(subject);
  }
}
