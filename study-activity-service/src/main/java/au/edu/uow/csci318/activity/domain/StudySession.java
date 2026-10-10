package au.edu.uow.csci318.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "study_sessions")
public class StudySession {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private UUID subjectId;

  @Column(nullable = false)
  private int durationMinutes;

  @Column(nullable = false)
  private LocalDate studyDate;

  @Column(nullable = false)
  @jakarta.persistence.Lob
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CLOB)
  private String description;

  @Column(nullable = false)
  private Instant recordedAt;

  private long eventRevision;

  @jakarta.persistence.Version
  @Column(columnDefinition = "bigint default 0", nullable = false)
  private long lockVersion;

  protected StudySession() {}

  public StudySession(
      UUID ownerId, UUID subjectId, int minutes, LocalDate date, String description) {
    this(ownerId, subjectId, minutes, date, description, LocalDate.now());
  }

  public StudySession(
      UUID ownerId,
      UUID subjectId,
      int minutes,
      LocalDate date,
      String description,
      LocalDate today) {
    this.id = UUID.randomUUID();
    this.ownerId = Objects.requireNonNull(ownerId);
    this.subjectId = Objects.requireNonNull(subjectId);
    edit(minutes, date, description, today);
    this.recordedAt = Instant.now();
  }

  public void edit(int minutes, LocalDate date, String description) {
    edit(minutes, date, description, LocalDate.now());
  }

  public void edit(int minutes, LocalDate date, String description, LocalDate today) {
    if (minutes <= 0 || minutes > 1440)
      throw new IllegalArgumentException("Study duration must be between 1 and 1440 minutes");
    if (date == null) throw new IllegalArgumentException("Study date is required");
    LocalDate checkedDate = date;
    if (checkedDate.isAfter(Objects.requireNonNull(today)))
      throw new IllegalArgumentException("Study date cannot be in the future");
    if (description == null || description.isBlank())
      throw new IllegalArgumentException("Activity description is required");
    if (description.length() > 2000)
      throw new IllegalArgumentException("Activity description must be at most 2000 characters");
    this.durationMinutes = minutes;
    this.studyDate = checkedDate;
    this.description = description.trim();
    this.eventRevision = Math.max(1, eventRevision) + 1;
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

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public LocalDate getStudyDate() {
    return studyDate;
  }

  public String getDescription() {
    return description;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public long getEventRevision() {
    return Math.max(1, eventRevision);
  }
}
