package au.edu.uow.csci318.planning.infrastructure;import au.edu.uow.csci318.planning.domain.StudyPlan;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface StudyPlanRepository extends JpaRepository<StudyPlan,UUID>{Optional<StudyPlan>findTopByOrderByCreatedAtDesc();}
