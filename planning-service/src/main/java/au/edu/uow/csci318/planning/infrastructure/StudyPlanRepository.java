package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {
  Optional<StudyPlan> findTopByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  Optional<StudyPlan> findTopByOwnerIdOrderByVersionDesc(UUID ownerId);

  java.util.List<StudyPlan> findByOwnerIdOrderByVersionDesc(UUID ownerId);

  Optional<StudyPlan> findByIdAndOwnerId(UUID id, UUID ownerId);
}
