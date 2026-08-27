package au.edu.uow.csci318.activity.infrastructure;

import au.edu.uow.csci318.activity.domain.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findByOwnerIdOrderByStudyDateDescRecordedAtDesc(UUID ownerId);
    List<StudySession> findByOwnerIdAndSubjectIdAndStudyDateBetween(UUID ownerId, UUID subjectId,
                                                                   LocalDate from, LocalDate to);
    Optional<StudySession> findByIdAndOwnerId(UUID id, UUID ownerId);
}
