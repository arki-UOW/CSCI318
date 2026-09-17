package au.edu.uow.csci318.subject.application;

import org.springframework.web.multipart.MultipartFile;

public interface SubjectDocumentReader {
  String extract(MultipartFile file);
}
