package au.edu.uow.csci318.account.infrastructure;

import au.edu.uow.csci318.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUsername(String username);
    boolean existsByUsername(String username);
}
