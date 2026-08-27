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
 private final InternalSubjectRepository subjects; private final InternalImportRepository imports; private final DocumentTextExtractor documents; private final OutlineExtraction extractor; private final ObjectMapper json; private final AssessmentImportClient assessments; private final SubjectConfirmationTransactions confirmations; private final ConfiguredChatModel configuredModel;
 SubjectApplicationService(InternalSubjectRepository subjects,InternalImportRepository imports,DocumentTextExtractor documents,OutlineExtraction extractor,ObjectMapper json,AssessmentImportClient assessments,SubjectConfirmationTransactions confirmations,ConfiguredChatModel configuredModel){this.subjects=subjects;this.imports=imports;this.documents=documents;this.extractor=extractor;this.json=json;this.assessments=assessments;this.confirmations=confirmations;this.configuredModel=configuredModel;}
 public ImportReview upload(UUID ownerId,MultipartFile file){
   try{String text=documents.extract(file);ExtractionResult result=extractor.extract(text);var item=imports.save(new SubjectOutlineImport(ownerId,file.getOriginalFilename(),text,json.writeValueAsString(result)));return review(item,result);}catch(java.io.IOException e){throw new IllegalArgumentException("The extracted document data could not be stored",e);}
 }
 public SubjectResponse createManual(UUID ownerId,String authorization,ManualSubjectRequest request){
   ExtractionResult extraction=new ExtractionResult(request.code(),request.name(),request.creditPoints(),request.assessments(),List.of());validate(ownerId,extraction);
   Subject subject=confirmations.createManual(ownerId,request);
   if(!request.assessments().isEmpty())assessments.importAssessments(authorization,subject.getId(),request.assessments());
   return response(subject);
 }
 public AiStatus aiStatus(){return configuredModel.status();}
 public SubjectResponse confirm(UUID ownerId,String authorization,UUID id,ConfirmImportRequest request){
   validate(ownerId,request.extraction());
   var prepared=confirmations.prepare(ownerId,id,request);
   if(prepared.alreadyConfirmed())return response(prepared.subject());
   if(!request.extraction().assessments().isEmpty())assessments.importAssessments(authorization,prepared.subject().getId(),request.extraction().assessments());
   confirmations.complete(ownerId,id,prepared.subject().getId());
   return response(prepared.subject());
 }
 public ImportReview getImport(UUID ownerId,UUID id){var i=imports.findByIdAndOwnerId(id,ownerId).orElseThrow(()->new NoSuchElementException("Import not found"));try{return review(i,json.readValue(i.getCandidateJson(),ExtractionResult.class));}catch(Exception e){throw new IllegalStateException("Stored extraction result is unreadable",e);}}
 public List<SubjectResponse> all(UUID ownerId){return subjects.findByOwnerIdOrderByCode(ownerId).stream().map(this::response).toList();}
 public SubjectResponse one(UUID ownerId,UUID id){return response(subjects.findByIdAndOwnerId(id,ownerId).orElseThrow(()->new NoSuchElementException("Subject not found")));}
 @Transactional public SubjectResponse target(UUID ownerId,UUID id,int minutes){var s=subjects.findByIdAndOwnerId(id,ownerId).orElseThrow(()->new NoSuchElementException("Subject not found"));s.changeWeeklyStudyTarget(minutes);return response(s);}
 private ImportReview review(SubjectOutlineImport i,ExtractionResult r){return new ImportReview(i.getId(),i.getFilename(),i.getStatus().name(),r);}
 private SubjectResponse response(Subject s){return new SubjectResponse(s.getId(),s.getCode(),s.getName(),s.getCreditPoints(),s.getWeeklyStudyTargetMinutes());}
 private void validate(UUID ownerId,ExtractionResult r){if(r==null)throw new IllegalArgumentException("Extraction result is required");new Subject(ownerId,r.subjectCode(),r.subjectName(),r.creditPoints(),0);if(r.assessments()==null)throw new IllegalArgumentException("Assessments are required");Set<String> seen=new HashSet<>();for(var a:r.assessments()){if(a.title()==null||a.title().isBlank())throw new IllegalArgumentException("Assessment title is required");if(a.weighting()!=null&&(a.weighting()<0||a.weighting()>100))throw new IllegalArgumentException("Assessment weighting is invalid");if(a.dueWeek()!=null&&(a.dueWeek()<1||a.dueWeek()>52))throw new IllegalArgumentException("Due week must be between 1 and 52");if(!seen.add(a.title().trim().toLowerCase()))throw new IllegalArgumentException("Duplicate assessment: "+a.title());}}
 public static class ServiceDependencyException extends RuntimeException{public ServiceDependencyException(String m,Throwable c){super(m,c);}}
}
