package au.edu.uow.csci318.subject.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="subject_outline_imports")
public class SubjectOutlineImport {
    public enum Status { EXTRACTED, CONFIRMED, FAILED }
    @Id private UUID id;
    @Column(nullable=false) private String filename;
    @Lob @Column(nullable=false) private String extractedText;
    @Lob @Column(nullable=false) private String candidateJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(nullable=false) private Instant createdAt;
    private UUID subjectId;
    protected SubjectOutlineImport() {}
    public SubjectOutlineImport(String filename,String text,String json){id=UUID.randomUUID();this.filename=filename;extractedText=text;candidateJson=json;status=Status.EXTRACTED;createdAt=Instant.now();}
    public void confirm(UUID subjectId){if(status!=Status.EXTRACTED)throw new IllegalStateException("Only extracted imports can be confirmed");this.subjectId=subjectId;status=Status.CONFIRMED;}
    public UUID getId(){return id;} public String getFilename(){return filename;} public String getExtractedText(){return extractedText;} public String getCandidateJson(){return candidateJson;} public Status getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public UUID getSubjectId(){return subjectId;}
}
