package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.domain.*;
import au.edu.uow.csci318.subject.dto.SubjectDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class SubjectApplicationService {
 private final InternalSubjectRepository subjects; private final InternalImportRepository imports; private final PdfTextExtractor pdf; private final OutlineExtraction extractor; private final ObjectMapper json; private final RestClient assessments;
 SubjectApplicationService(InternalSubjectRepository subjects,InternalImportRepository imports,PdfTextExtractor pdf,OutlineExtraction extractor,ObjectMapper json,RestClient.Builder builder,@Value("${services.assessment-url}")String url){this.subjects=subjects;this.imports=imports;this.pdf=pdf;this.extractor=extractor;this.json=json;this.assessments=builder.baseUrl(url).build();}
 @Transactional public ImportReview upload(MultipartFile file){
   if(file==null||file.isEmpty())throw new IllegalArgumentException("A non-empty PDF is required");if(file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase().endsWith(".pdf"))throw new IllegalArgumentException("Only PDF subject outlines are supported");if(file.getSize()>10_000_000)throw new IllegalArgumentException("PDF must be 10 MB or smaller");
   try{String text=pdf.extract(file.getBytes());if(text.isBlank())throw new IllegalArgumentException("The PDF does not contain extractable text");ExtractionResult result=extractor.extract(text);var item=imports.save(new SubjectOutlineImport(file.getOriginalFilename(),text,json.writeValueAsString(result)));return review(item,result);}catch(java.io.IOException e){throw new IllegalArgumentException("The uploaded PDF could not be processed",e);}
 }
 @Transactional public SubjectResponse confirm(UUID id,ConfirmImportRequest request){
   var item=imports.findById(id).orElseThrow(()->new NoSuchElementException("Import not found"));validate(request.extraction());
   if(subjects.findByCode(request.extraction().subjectCode().toUpperCase()).isPresent())throw new IllegalArgumentException("A subject with this code already exists");
   var saved=subjects.save(new Subject(request.extraction().subjectCode(),request.extraction().subjectName(),request.extraction().creditPoints(),request.weeklyStudyTargetMinutes()));
   try{assessments.post().uri("/api/assessments/import").body(new AssessmentImport(saved.getId(),request.extraction().assessments())).retrieve().toBodilessEntity();}catch(Exception e){throw new ServiceDependencyException("Assessment Service rejected the confirmed assessments",e);}
   item.confirm(saved.getId());return response(saved);
 }
 public ImportReview getImport(UUID id){var i=imports.findById(id).orElseThrow(()->new NoSuchElementException("Import not found"));try{return review(i,json.readValue(i.getCandidateJson(),ExtractionResult.class));}catch(Exception e){throw new IllegalStateException("Stored extraction result is unreadable",e);}}
 public List<SubjectResponse> all(){return subjects.findAll().stream().map(this::response).toList();}
 public SubjectResponse one(UUID id){return response(subjects.findById(id).orElseThrow(()->new NoSuchElementException("Subject not found")));}
 @Transactional public SubjectResponse target(UUID id,int minutes){var s=subjects.findById(id).orElseThrow(()->new NoSuchElementException("Subject not found"));s.changeWeeklyStudyTarget(minutes);return response(s);}
 private ImportReview review(SubjectOutlineImport i,ExtractionResult r){return new ImportReview(i.getId(),i.getFilename(),i.getStatus().name(),r);}
 private SubjectResponse response(Subject s){return new SubjectResponse(s.getId(),s.getCode(),s.getName(),s.getCreditPoints(),s.getWeeklyStudyTargetMinutes());}
 private void validate(ExtractionResult r){new Subject(r.subjectCode(),r.subjectName(),r.creditPoints(),0);Set<String> seen=new HashSet<>();for(var a:r.assessments()){if(a.title()==null||a.title().isBlank())throw new IllegalArgumentException("Assessment title is required");if(a.weighting()!=null&&(a.weighting()<0||a.weighting()>100))throw new IllegalArgumentException("Assessment weighting is invalid");if(!seen.add(a.title().trim().toLowerCase()))throw new IllegalArgumentException("Duplicate assessment: "+a.title());}}
 record AssessmentImport(UUID subjectId,List<AssessmentCandidate> assessments){}
 public static class ServiceDependencyException extends RuntimeException{public ServiceDependencyException(String m,Throwable c){super(m,c);}}
}
