package au.edu.uow.csci318.activity.infrastructure;import au.edu.uow.csci318.activity.domain.StudySession;import org.springframework.data.jpa.repository.JpaRepository;import java.time.*;import java.util.*;
public interface StudySessionRepository extends JpaRepository<StudySession,UUID>{List<StudySession>findBySubjectIdAndStudyDateBetween(UUID id,LocalDate from,LocalDate to);}
