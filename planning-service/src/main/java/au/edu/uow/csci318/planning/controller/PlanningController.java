package au.edu.uow.csci318.planning.controller;

import au.edu.uow.csci318.planning.application.PlanningApplicationService;
import au.edu.uow.csci318.planning.dto.PlanningDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/planning")
@CrossOrigin
public class PlanningController {
    private final PlanningApplicationService service;

    public PlanningController(PlanningApplicationService service) {
        this.service = service;
    }

    @GetMapping("/workload")
    public WorkloadSummary workload() {
        return service.workload();
    }

    @GetMapping("/this-week")
    public ThisWeek week() {
        return service.thisWeek();
    }

    @GetMapping("/ai/status")
    public AiStatus aiStatus() {
        return service.aiStatus();
    }

    @PostMapping("/availability/chat")
    public AvailabilityChatResponse updateAvailability(@Valid @RequestBody AvailabilityChatRequest request) {
        return service.updateAvailability(request);
    }

    @GetMapping("/plans/latest")
    public ResponseEntity<PlanResponse> latest() {
        return service.latest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse generate(@Valid @RequestBody PlanRequest request) {
        return service.generate(request);
    }

    @PostMapping("/plans/{id}/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse regenerate(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return service.regenerate(id, request);
    }
}
