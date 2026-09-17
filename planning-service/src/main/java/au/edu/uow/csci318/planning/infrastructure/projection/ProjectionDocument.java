package au.edu.uow.csci318.planning.infrastructure.projection;

import jakarta.persistence.*;
import java.util.UUID;

/** Durable local CQRS read model; this is not a command-side domain aggregate. */
@Entity
@Table(
    name = "planning_projections",
    indexes = @Index(name = "idx_projection_owner_kind", columnList = "owner_id,kind"))
public class ProjectionDocument {
  @Id private String id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String kind;

  private long revision;

  @Lob
  @Column(nullable = false, columnDefinition = "CLOB")
  private String body;

  protected ProjectionDocument() {}

  ProjectionDocument(String id, UUID ownerId, String kind) {
    this.id = id;
    this.ownerId = ownerId;
    this.kind = kind;
  }

  boolean replace(long revision, String body) {
    if (revision <= this.revision) return false;
    this.revision = revision;
    this.body = body;
    return true;
  }

  String getBody() {
    return body;
  }
}
