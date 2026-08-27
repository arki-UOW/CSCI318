package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.domain.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {
    Optional<StudyPlan> findTopByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<StudyPlan> findByIdAndOwnerId(UUID id, UUID ownerId);
}
