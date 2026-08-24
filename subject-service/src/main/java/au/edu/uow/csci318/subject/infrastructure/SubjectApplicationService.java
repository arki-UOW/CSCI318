package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.*;
import au.edu.uow.csci318.subject.dto.SubjectDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class SubjectApplicationService {
 private final InternalSubjectRepository subjects; private final InternalImportRepository imports; private final PdfTextExtractor pdf; private final OutlineExtraction extractor; private final ObjectMapper json; private final AssessmentImportClient assessments; private final SubjectConfirmationTransactions confirmations;
 SubjectApplicationService(InternalSubjectRepository subjects,InternalImportRepository imports,PdfTextExtractor pdf,OutlineExtraction extractor,ObjectMapper json,AssessmentImportClient assessments,SubjectConfirmationTransactions confirmations){this.subjects=subjects;this.imports=imports;this.pdf=pdf;this.extractor=extractor;this.json=json;this.assessments=assessments;this.confirmations=confirmations;}
 @Transactional public ImportReview upload(MultipartFile file){
   if(file==null||file.isEmpty())throw new IllegalArgumentException("A non-empty PDF is required");if(file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase().endsWith(".pdf"))throw new IllegalArgumentException("Only PDF subject outlines are supported");if(file.getSize()>10_000_000)throw new IllegalArgumentException("PDF must be 10 MB or smaller");
   try{byte[] bytes=file.getBytes();String text=pdf.extract(bytes);ExtractionResult result=extractor.extract(bytes,text);var item=imports.save(new SubjectOutlineImport(file.getOriginalFilename(),text,json.writeValueAsString(result)));return review(item,result);}catch(java.io.IOException e){throw new IllegalArgumentException("The uploaded PDF could not be processed",e);}
 }
 public SubjectResponse confirm(UUID id,ConfirmImportRequest request){
   validate(request.extraction());
   var prepared=confirmations.prepare(id,request);
   if(prepared.alreadyConfirmed())return response(prepared.subject());
   assessments.importAssessments(prepared.subject().getId(),request.extraction().assessments());
   confirmations.complete(id,prepared.subject().getId());
   return response(prepared.subject());
 }
 public ImportReview getImport(UUID id){var i=imports.findById(id).orElseThrow(()->new NoSuchElementException("Import not found"));try{return review(i,json.readValue(i.getCandidateJson(),ExtractionResult.class));}catch(Exception e){throw new IllegalStateException("Stored extraction result is unreadable",e);}}
 public List<SubjectResponse> all(){return subjects.findAll().stream().map(this::response).toList();}
 public SubjectResponse one(UUID id){return response(subjects.findById(id).orElseThrow(()->new NoSuchElementException("Subject not found")));}
 @Transactional public SubjectResponse target(UUID id,int minutes){var s=subjects.findById(id).orElseThrow(()->new NoSuchElementException("Subject not found"));s.changeWeeklyStudyTarget(minutes);return response(s);}
 private ImportReview review(SubjectOutlineImport i,ExtractionResult r){return new ImportReview(i.getId(),i.getFilename(),i.getStatus().name(),r);}
 private SubjectResponse response(Subject s){return new SubjectResponse(s.getId(),s.getCode(),s.getName(),s.getCreditPoints(),s.getWeeklyStudyTargetMinutes());}
 private void validate(ExtractionResult r){if(r==null)throw new IllegalArgumentException("Extraction result is required");new Subject(r.subjectCode(),r.subjectName(),r.creditPoints(),0);if(r.assessments()==null)throw new IllegalArgumentException("Assessments are required");Set<String> seen=new HashSet<>();for(var a:r.assessments()){if(a.title()==null||a.title().isBlank())throw new IllegalArgumentException("Assessment title is required");if(a.weighting()!=null&&(a.weighting()<0||a.weighting()>100))throw new IllegalArgumentException("Assessment weighting is invalid");if(a.dueWeek()!=null&&(a.dueWeek()<1||a.dueWeek()>20))throw new IllegalArgumentException("Due week must be between 1 and 20");if(!seen.add(a.title().trim().toLowerCase()))throw new IllegalArgumentException("Duplicate assessment: "+a.title());}}
 public static class ServiceDependencyException extends RuntimeException{public ServiceDependencyException(String m,Throwable c){super(m,c);}}
}
