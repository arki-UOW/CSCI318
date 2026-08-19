package au.edu.uow.csci318.assessment.infrastructure;
import au.edu.uow.csci318.assessment.domain.Assessment;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface AssessmentRepository extends JpaRepository<Assessment,UUID>{boolean existsBySubjectIdAndTitleIgnoreCase(UUID subjectId,String title);List<Assessment>findAllByOrderByDueDateAsc();}
