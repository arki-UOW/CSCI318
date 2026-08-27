package au.edu.uow.csci318.assessment.infrastructure;
import au.edu.uow.csci318.assessment.domain.Assessment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface AssessmentRepository extends JpaRepository<Assessment,UUID>{boolean existsByOwnerIdAndSubjectIdAndTitleIgnoreCase(UUID ownerId,UUID subjectId,String title);Optional<Assessment>findByOwnerIdAndSubjectIdAndTitleIgnoreCase(UUID ownerId,UUID subjectId,String title);Optional<Assessment>findByIdAndOwnerId(UUID id,UUID ownerId);List<Assessment>findByOwnerIdOrderByDueDateAsc(UUID ownerId);}
