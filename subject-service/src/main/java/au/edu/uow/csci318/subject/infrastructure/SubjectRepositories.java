package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public final class SubjectRepositories { private SubjectRepositories(){} }

interface InternalSubjectRepository extends JpaRepository<Subject,UUID> { Optional<Subject> findByOwnerIdAndCode(UUID ownerId,String code); Optional<Subject> findByIdAndOwnerId(UUID id,UUID ownerId); List<Subject> findByOwnerIdOrderByCode(UUID ownerId); }
interface InternalImportRepository extends JpaRepository<SubjectOutlineImport,UUID> { Optional<SubjectOutlineImport> findByIdAndOwnerId(UUID id,UUID ownerId); }
