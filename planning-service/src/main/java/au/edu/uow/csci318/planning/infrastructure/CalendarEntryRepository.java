package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.domain.CalendarEntry.EntryStatus;
import au.edu.uow.csci318.planning.domain.CalendarEntry.Origin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, UUID> {
    List<CalendarEntry> findByOwnerIdAndStartAtBetweenOrderByStartAt(UUID ownerId,
                                                                    LocalDateTime from,
                                                                    LocalDateTime to);
    Optional<CalendarEntry> findByIdAndOwnerId(UUID id, UUID ownerId);
    void deleteByOwnerIdAndOriginAndStatus(UUID ownerId, Origin origin, EntryStatus status);
}
