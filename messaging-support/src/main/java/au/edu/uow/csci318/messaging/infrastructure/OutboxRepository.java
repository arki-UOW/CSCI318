package au.edu.uow.csci318.messaging.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from OutboxMessage m order by m.createdAt, m.id")
  List<OutboxMessage> pending(Pageable page);
}
