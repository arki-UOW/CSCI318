package au.edu.uow.csci318.planning.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "calendar_entries",
    indexes = {@Index(name = "idx_calendar_owner_start", columnList = "owner_id,start_at")})
public class CalendarEntry {
  public enum EntryType {
    TASK,
    STUDY_SESSION,
    REVIEW
  }

  public enum EntryStatus {
    PLANNED,
    COMPLETED
  }

  public enum Origin {
    MANUAL,
    AI_PLAN,
    SPACED_REPETITION
  }

  @Id private UUID id;

  @Column(nullable = false)
  private UUID ownerId;

  private UUID subjectId;
  private UUID assessmentId;
  private UUID planId;
  private UUID sourceEntryId;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EntryType type;

  @Column(nullable = false)
  private LocalDateTime startAt;

  @Column(nullable = false)
  private LocalDateTime endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EntryStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Origin origin;

  @Column(nullable = false)
  private boolean spacedRepetition;

  @Column(nullable = false)
  private int repetitionStage;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant completedAt;
  private long eventRevision;

  @Version
  @Column(columnDefinition = "bigint default 0", nullable = false)
  private long lockVersion;

  protected CalendarEntry() {}

  public CalendarEntry(
      UUID ownerId,
      UUID subjectId,
      UUID assessmentId,
      UUID planId,
      UUID sourceEntryId,
      String title,
      String description,
      EntryType type,
      LocalDateTime startAt,
      LocalDateTime endAt,
      Origin origin,
      boolean spacedRepetition,
      int repetitionStage) {
    id = UUID.randomUUID();
    this.ownerId = Objects.requireNonNull(ownerId);
    this.subjectId = subjectId;
    this.assessmentId = assessmentId;
    this.planId = planId;
    this.sourceEntryId = sourceEntryId;
    this.origin = Objects.requireNonNull(origin);
    this.spacedRepetition = spacedRepetition;
    this.repetitionStage = Math.max(0, repetitionStage);
    status = EntryStatus.PLANNED;
    createdAt = Instant.now();
    edit(title, description, type, startAt, endAt, subjectId, assessmentId, spacedRepetition);
  }

  public void edit(
      String title,
      String description,
      EntryType type,
      LocalDateTime startAt,
      LocalDateTime endAt,
      UUID subjectId,
      UUID assessmentId,
      boolean spacedRepetition) {
    String cleanTitle = title == null ? "" : title.trim();
    if (cleanTitle.isBlank() || cleanTitle.length() > 160) {
      throw new IllegalArgumentException("Calendar title must contain 1 to 160 characters");
    }
    if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("Calendar end time must be after its start time");
    }
    if (java.time.Duration.between(startAt, endAt).compareTo(java.time.Duration.ofHours(24)) > 0) {
      throw new IllegalArgumentException("A calendar entry cannot be longer than 24 hours");
    }
    this.title = cleanTitle;
    this.description = description == null ? "" : description.trim();
    this.type = Objects.requireNonNull(type);
    this.startAt = startAt;
    this.endAt = endAt;
    this.subjectId = subjectId;
    this.assessmentId = assessmentId;
    this.spacedRepetition = spacedRepetition;
    updatedAt = Instant.now();
    eventRevision = Math.max(1, eventRevision) + 1;
  }

  public void complete() {
    if (status == EntryStatus.COMPLETED) {
      throw new IllegalStateException("Calendar entry is already completed");
    }
    status = EntryStatus.COMPLETED;
    completedAt = Instant.now();
    updatedAt = completedAt;
    eventRevision = Math.max(1, eventRevision) + 1;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public UUID getAssessmentId() {
    return assessmentId;
  }

  public UUID getPlanId() {
    return planId;
  }

  public UUID getSourceEntryId() {
    return sourceEntryId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public EntryType getType() {
    return type;
  }

  public LocalDateTime getStartAt() {
    return startAt;
  }

  public LocalDateTime getEndAt() {
    return endAt;
  }

  public EntryStatus getStatus() {
    return status;
  }

  public Origin getOrigin() {
    return origin;
  }

  public boolean isSpacedRepetition() {
    return spacedRepetition;
  }

  public int getRepetitionStage() {
    return repetitionStage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public long getEventRevision() {
    return Math.max(1, eventRevision);
  }
}
