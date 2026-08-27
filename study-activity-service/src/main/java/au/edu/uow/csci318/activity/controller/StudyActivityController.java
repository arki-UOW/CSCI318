package au.edu.uow.csci318.activity.controller;

import au.edu.uow.csci318.activity.application.StudyActivityApplicationService;
import au.edu.uow.csci318.activity.application.StudyActivityApplicationService.*;
import au.edu.uow.csci318.activity.infrastructure.IdentityClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/study-sessions")
@CrossOrigin
public class StudyActivityController {
    private final StudyActivityApplicationService service;
    private final IdentityClient identity;
    public StudyActivityController(StudyActivityApplicationService service, IdentityClient identity) {
        this.service = service;
        this.identity = identity;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response record(@RequestHeader("Authorization") String auth, @Valid @RequestBody CreateRequest request) {
        return service.record(identity.require(auth), auth, request);
    }
    @PatchMapping("/{id}")
    public Response update(@RequestHeader("Authorization") String auth, @PathVariable UUID id,
                           @Valid @RequestBody UpdateRequest request) {
        return service.update(identity.require(auth), id, request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("Authorization") String auth, @PathVariable UUID id) {
        service.delete(identity.require(auth), id);
    }
    @GetMapping
    public List<Response> all(@RequestHeader("Authorization") String auth) { return service.all(identity.require(auth)); }
    @GetMapping("/summary")
    public Summary summary(@RequestHeader("Authorization") String auth, @RequestParam UUID subjectId,
                           @RequestParam(required = false) LocalDate weekOf) {
        return service.summary(identity.require(auth), subjectId, weekOf);
    }
}
