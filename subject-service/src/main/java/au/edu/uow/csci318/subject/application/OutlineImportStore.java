package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import java.util.*;

public interface OutlineImportStore {
  Optional<SubjectOutlineImport> outline(UUID ownerId, UUID id);

  SubjectOutlineImport storeOutline(SubjectOutlineImport outline);
}
