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
 private final SubjectApplicationService service; private final au.edu.uow.csci318.subject.infrastructure.IdentityClient identity; public SubjectController(SubjectApplicationService service,au.edu.uow.csci318.subject.infrastructure.IdentityClient identity){this.service=service;this.identity=identity;}
 @PostMapping(value="/subject-outlines",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public ImportReview upload(@RequestHeader("Authorization")String auth,@RequestPart("file")MultipartFile file){return service.upload(identity.require(auth),file);}
 @GetMapping("/subject-outlines/{id}") public ImportReview review(@RequestHeader("Authorization")String auth,@PathVariable UUID id){return service.getImport(identity.require(auth),id);}
 @PostMapping("/subject-outlines/{id}/confirm") @ResponseStatus(HttpStatus.CREATED) public SubjectResponse confirm(@RequestHeader("Authorization")String auth,@PathVariable UUID id,@Valid @RequestBody ConfirmImportRequest request){return service.confirm(identity.require(auth),auth,id,request);}
 @GetMapping("/subjects") public List<SubjectResponse> subjects(@RequestHeader("Authorization")String auth){return service.all(identity.require(auth));}
 @PostMapping("/subjects") @ResponseStatus(HttpStatus.CREATED) public SubjectResponse createManual(@RequestHeader("Authorization")String auth,@Valid @RequestBody ManualSubjectRequest request){return service.createManual(identity.require(auth),auth,request);}
 @GetMapping("/subjects/{id}") public SubjectResponse subject(@RequestHeader("Authorization")String auth,@PathVariable UUID id){return service.one(identity.require(auth),id);}
 @PatchMapping("/subjects/{id}/study-target") public SubjectResponse target(@RequestHeader("Authorization")String auth,@PathVariable UUID id,@RequestBody Map<String,Integer> body){return service.target(identity.require(auth),id,body.getOrDefault("minutes",-1));}
 @GetMapping("/ai/status") public AiStatus aiStatus(@RequestHeader("Authorization")String auth){identity.require(auth);return service.aiStatus();}
}
