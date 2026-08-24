package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.dto.SubjectDtos.AssessmentCandidate;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ConfirmImportRequest;
import au.edu.uow.csci318.subject.dto.SubjectDtos.ExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectApplicationServiceTest {
    @Mock private InternalSubjectRepository subjects;
    @Mock private InternalImportRepository imports;
    @Mock private DocumentTextExtractor documents;
    @Mock private OutlineExtraction extractor;
    @Mock private AssessmentImportClient assessments;
    @Mock private SubjectConfirmationTransactions confirmations;
    @Mock private ConfiguredChatModel configuredModel;

    private SubjectApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SubjectApplicationService(
                subjects, imports, documents, extractor, new ObjectMapper(), assessments,
                confirmations, configuredModel);
    }

    @Test
    void commitsSubjectPreparationBeforeCallingAssessmentService() {
        UUID importId = UUID.randomUUID();
        ConfirmImportRequest request = request();
        Subject subject = new Subject("CSCI318", "Software Engineering", 6, 240);
        when(confirmations.prepare(importId, request))
                .thenReturn(new SubjectConfirmationTransactions.PreparedConfirmation(subject, false));

        var response = service.confirm(importId, request);

        assertEquals(subject.getId(), response.id());
        InOrder order = inOrder(confirmations, assessments);
        order.verify(confirmations).prepare(importId, request);
        order.verify(assessments).importAssessments(subject.getId(), request.extraction().assessments());
        order.verify(confirmations).complete(importId, subject.getId());
    }

    @Test
    void retryOfCompletedConfirmationDoesNotImportAssessmentsAgain() {
        UUID importId = UUID.randomUUID();
        ConfirmImportRequest request = request();
        Subject subject = new Subject("CSCI318", "Software Engineering", 6, 240);
        when(confirmations.prepare(importId, request))
                .thenReturn(new SubjectConfirmationTransactions.PreparedConfirmation(subject, true));

        service.confirm(importId, request);

        verify(assessments, never()).importAssessments(subject.getId(), request.extraction().assessments());
        verify(confirmations, never()).complete(importId, subject.getId());
    }

    private ConfirmImportRequest request() {
        AssessmentCandidate assessment = new AssessmentCandidate(
                "Architecture Report", "Report", 25.0, null, 8, null, 10.0, 0.9, null);
        ExtractionResult extraction = new ExtractionResult(
                "CSCI318", "Software Engineering", 6, List.of(assessment), List.of());
        return new ConfirmImportRequest(extraction, 240);
    }
}
