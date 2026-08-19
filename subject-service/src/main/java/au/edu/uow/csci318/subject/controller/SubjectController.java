package au.edu.uow.csci318.subject.controller;

import au.edu.uow.csci318.subject.dto.SubjectDtos.*;
import au.edu.uow.csci318.subject.infrastructure.SubjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api") @CrossOrigin
public class SubjectController {
 private final SubjectApplicationService service; public SubjectController(SubjectApplicationService service){this.service=service;}
 @PostMapping(value="/subject-outlines",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public ImportReview upload(@RequestPart("file")MultipartFile file){return service.upload(file);}
 @GetMapping("/subject-outlines/{id}") public ImportReview review(@PathVariable UUID id){return service.getImport(id);}
 @PostMapping("/subject-outlines/{id}/confirm") @ResponseStatus(HttpStatus.CREATED) public SubjectResponse confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmImportRequest request){return service.confirm(id,request);}
 @GetMapping("/subjects") public List<SubjectResponse> subjects(){return service.all();}
 @GetMapping("/subjects/{id}") public SubjectResponse subject(@PathVariable UUID id){return service.one(id);}
 @PatchMapping("/subjects/{id}/study-target") public SubjectResponse target(@PathVariable UUID id,@RequestBody Map<String,Integer> body){return service.target(id,body.getOrDefault("minutes",-1));}
}
