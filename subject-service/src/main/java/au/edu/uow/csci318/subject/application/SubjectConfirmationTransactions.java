package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ConfirmImportRequest;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ManualSubjectRequest;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubjectConfirmationTransactions {
  private final SubjectStore subjects;
  private final OutlineImportStore imports;
  private final SubjectEvents events;

  public SubjectConfirmationTransactions(
      SubjectStore subjects, OutlineImportStore imports, SubjectEvents events) {
    this.subjects = subjects;
    this.imports = imports;
    this.events = events;
  }

  @Transactional
  public PreparedConfirmation prepare(UUID ownerId, UUID importId, ConfirmImportRequest request) {
    SubjectOutlineImport item =
        imports
            .outline(ownerId, importId)
            .orElseThrow(() -> new NoSuchElementException("Import not found"));
    if (item.getStatus() == SubjectOutlineImport.Status.CONFIRMED
        || item.getStatus() == SubjectOutlineImport.Status.CONFIRMING) {
      Subject existing =
          subjects
              .subject(ownerId, item.getSubjectId())
              .orElseThrow(
                  () -> new IllegalStateException("The pending subject could not be found"));
      if (!existing.getCode().equalsIgnoreCase(request.extraction().subjectCode())) {
        throw new IllegalArgumentException(
            "The subject code cannot be changed after confirmation has started");
      }
      return new PreparedConfirmation(
          existing, item.getStatus() == SubjectOutlineImport.Status.CONFIRMED);
    }
    if (item.getStatus() != SubjectOutlineImport.Status.EXTRACTED) {
      throw new IllegalStateException("This outline import cannot be confirmed");
    }
    String code = request.extraction().subjectCode().toUpperCase();
    if (subjects.byCode(ownerId, code).isPresent()) {
      throw new IllegalArgumentException("A subject with this code already exists");
    }
    Subject subject =
        subjects.storeSubject(
            new Subject(
                ownerId,
                code,
                request.extraction().subjectName(),
                request.extraction().creditPoints(),
                request.weeklyStudyTargetMinutes()));
    item.beginConfirmation(subject.getId());
    events.publish("SubjectCreated", subject);
    return new PreparedConfirmation(subject, false);
  }

  @Transactional
  public void complete(UUID ownerId, UUID importId, UUID subjectId) {
    SubjectOutlineImport item =
        imports
            .outline(ownerId, importId)
            .orElseThrow(() -> new NoSuchElementException("Import not found"));
    if (!subjectId.equals(item.getSubjectId())) {
      throw new IllegalStateException("The confirmed subject does not match the outline import");
    }
    if (item.getStatus() == SubjectOutlineImport.Status.CONFIRMING) {
      item.confirm();
    }
  }

  @Transactional
  public Subject createManual(UUID ownerId, ManualSubjectRequest request) {
    return subjects
        .byCode(ownerId, request.code().toUpperCase())
        .map(
            existing -> {
              if (!existing.getName().equalsIgnoreCase(request.name())) {
                throw new IllegalArgumentException(
                    "A different subject already uses code " + request.code());
              }
              return existing;
            })
        .orElseGet(
            () -> {
              Subject subject =
                  subjects.storeSubject(
                      new Subject(
                          ownerId,
                          request.code(),
                          request.name(),
                          request.creditPoints(),
                          request.weeklyStudyTargetMinutes()));
              events.publish("SubjectCreated", subject);
              return subject;
            });
  }

  @Transactional
  public Subject changeTarget(UUID ownerId, UUID id, int minutes) {
    Subject subject =
        subjects
            .subject(ownerId, id)
            .orElseThrow(() -> new NoSuchElementException("Subject not found"));
    subject.changeWeeklyStudyTarget(minutes);
    events.publish("SubjectTargetChanged", subject);
    return subject;
  }

  public record PreparedConfirmation(Subject subject, boolean alreadyConfirmed) {}
}
