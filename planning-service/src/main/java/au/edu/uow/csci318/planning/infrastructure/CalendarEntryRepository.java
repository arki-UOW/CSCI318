package au.edu.uow.csci318.planning.infrastructure;

import au.edu.uow.csci318.planning.domain.CalendarEntry;
import au.edu.uow.csci318.planning.domain.CalendarEntry.EntryStatus;
import au.edu.uow.csci318.planning.domain.CalendarEntry.Origin;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, UUID> {
  List<CalendarEntry> findByOwnerIdAndStartAtBetweenOrderByStartAt(
      UUID ownerId, LocalDateTime from, LocalDateTime to);

  Optional<CalendarEntry> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Query(
      "select e from CalendarEntry e where e.ownerId = :ownerId and e.startAt < :to and e.endAt >"
          + " :from")
  List<CalendarEntry> findOverlapping(UUID ownerId, LocalDateTime from, LocalDateTime to);

  void deleteByOwnerIdAndOriginAndStatus(UUID ownerId, Origin origin, EntryStatus status);
}
