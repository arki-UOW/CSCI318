package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.subject.dto.SubjectDtos.ExtractionResult;

public interface OutlineExtraction {
  ExtractionResult extract(String extractedText);
}
