package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.application.OutlineImportStore;
import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
class JpaOutlineImportStore implements OutlineImportStore {
  private final InternalImportRepository repository;

  JpaOutlineImportStore(InternalImportRepository repository) {
    this.repository = repository;
  }

  public Optional<SubjectOutlineImport> outline(UUID owner, UUID id) {
    return repository.findByIdAndOwnerId(id, owner);
  }

  public SubjectOutlineImport storeOutline(SubjectOutlineImport outline) {
    return repository.save(outline);
  }
}
