package au.edu.uow.csci318.account.infrastructure;

import au.edu.uow.csci318.account.domain.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountSessionRepository extends JpaRepository<AccountSession, UUID> {
    Optional<AccountSession> findByTokenHash(String tokenHash);
}
