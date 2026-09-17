package au.edu.uow.csci318.planning.infrastructure.projection;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectionDocumentRepository extends JpaRepository<ProjectionDocument, String> {
  List<ProjectionDocument> findByOwnerIdAndKind(UUID ownerId, String kind);
}
