package au.edu.uow.csci318.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_sessions", uniqueConstraints = @UniqueConstraint(columnNames = "token_hash"))
public class AccountSession {
    @Id private UUID id;
    @Column(nullable = false) private UUID accountId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant lastSeenAt;

    protected AccountSession() {}

    public AccountSession(UUID accountId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastSeenAt = createdAt;
    }

    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    public void touch() { lastSeenAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
