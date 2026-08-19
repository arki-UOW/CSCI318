package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public final class SubjectRepositories { private SubjectRepositories(){} }

interface InternalSubjectRepository extends JpaRepository<Subject,UUID> { Optional<Subject> findByCode(String code); }
interface InternalImportRepository extends JpaRepository<SubjectOutlineImport,UUID> {}
