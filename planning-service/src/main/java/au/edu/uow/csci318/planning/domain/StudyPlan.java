package au.edu.uow.csci318.planning.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "study_plans")
public class StudyPlan {
    @Id private UUID id;
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    @Column(nullable = false) private int version;
    @Lob @Column(nullable = false) private String itemsJson;
    @Column(nullable = false) private String explanation;
    @Column(nullable = false) private Instant createdAt;

    protected StudyPlan() {}

    public StudyPlan(UUID ownerId, LocalDate start, LocalDate end, int version, String json,
                     String explanation) {
        if (start == null || end == null || end.isBefore(start)
                || ChronoUnit.DAYS.between(start, end) > 370) {
            throw new IllegalArgumentException("Planning period must be between one day and 371 days");
        }
        id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(ownerId);
        startDate = start;
        endDate = end;
        this.version = version;
        itemsJson = Objects.requireNonNull(json);
        this.explanation = explanation == null ? "" : explanation;
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getVersion() { return version; }
    public String getItemsJson() { return itemsJson; }
    public String getExplanation() { return explanation; }
    public Instant getCreatedAt() { return createdAt; }
}
