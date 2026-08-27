package au.edu.uow.csci318.planning.controller;

import au.edu.uow.csci318.planning.application.StudyAssistantService;
import au.edu.uow.csci318.planning.dto.AssistantDtos.*;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planning/assistant")
@CrossOrigin
public class StudyAssistantController {
    private final StudyAssistantService service;
    private final IdentityClient identity;

    public StudyAssistantController(StudyAssistantService service, IdentityClient identity) {
        this.service = service;
        this.identity = identity;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestHeader("Authorization") String authorization,
                             @Valid @RequestBody ChatRequest request) {
        return service.chat(identity.require(authorization), authorization, request);
    }
}
