package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.domain.SubjectOutlineImport;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ConfirmImportRequest;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ManualSubjectRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Component
class SubjectConfirmationTransactions {
    private final InternalSubjectRepository subjects;
    private final InternalImportRepository imports;

    SubjectConfirmationTransactions(InternalSubjectRepository subjects, InternalImportRepository imports) {
        this.subjects = subjects;
        this.imports = imports;
    }

    @Transactional
    PreparedConfirmation prepare(UUID ownerId, UUID importId, ConfirmImportRequest request) {
        SubjectOutlineImport item = imports.findByIdAndOwnerId(importId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Import not found"));
        if (item.getStatus() == SubjectOutlineImport.Status.CONFIRMED
                || item.getStatus() == SubjectOutlineImport.Status.CONFIRMING) {
            Subject existing = subjects.findByIdAndOwnerId(item.getSubjectId(), ownerId)
                    .orElseThrow(() -> new IllegalStateException("The pending subject could not be found"));
            if (!existing.getCode().equalsIgnoreCase(request.extraction().subjectCode())) {
                throw new IllegalArgumentException(
                        "The subject code cannot be changed after confirmation has started");
            }
            return new PreparedConfirmation(existing, item.getStatus() == SubjectOutlineImport.Status.CONFIRMED);
        }
        if (item.getStatus() != SubjectOutlineImport.Status.EXTRACTED) {
            throw new IllegalStateException("This outline import cannot be confirmed");
        }
        String code = request.extraction().subjectCode().toUpperCase();
        if (subjects.findByOwnerIdAndCode(ownerId, code).isPresent()) {
            throw new IllegalArgumentException("A subject with this code already exists");
        }
        Subject subject = subjects.save(new Subject(ownerId,
                code,
                request.extraction().subjectName(),
                request.extraction().creditPoints(),
                request.weeklyStudyTargetMinutes()));
        item.beginConfirmation(subject.getId());
        return new PreparedConfirmation(subject, false);
    }

    @Transactional
    void complete(UUID ownerId, UUID importId, UUID subjectId) {
        SubjectOutlineImport item = imports.findByIdAndOwnerId(importId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Import not found"));
        if (!subjectId.equals(item.getSubjectId())) {
            throw new IllegalStateException("The confirmed subject does not match the outline import");
        }
        if (item.getStatus() == SubjectOutlineImport.Status.CONFIRMING) {
            item.confirm();
        }
    }

    @Transactional
    Subject createManual(UUID ownerId, ManualSubjectRequest request) {
        return subjects.findByOwnerIdAndCode(ownerId, request.code().toUpperCase())
                .map(existing -> {
                    if (!existing.getName().equalsIgnoreCase(request.name())) {
                        throw new IllegalArgumentException("A different subject already uses code " + request.code());
                    }
                    return existing;
                })
                .orElseGet(() -> subjects.save(new Subject(ownerId,
                        request.code(), request.name(), request.creditPoints(), request.weeklyStudyTargetMinutes())));
    }

    record PreparedConfirmation(Subject subject, boolean alreadyConfirmed) {
    }
}
