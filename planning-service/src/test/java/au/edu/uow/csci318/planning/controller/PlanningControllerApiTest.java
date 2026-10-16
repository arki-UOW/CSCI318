package au.edu.uow.csci318.planning.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import au.edu.uow.csci318.planning.application.PlanningApplicationService;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanRequest;
import au.edu.uow.csci318.planning.dto.PlanningDtos.PlanResponse;
import au.edu.uow.csci318.planning.infrastructure.IdentityClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlanningControllerApiTest {
  @Test
  void startsAgenticGenerationThroughAuthenticatedApi() throws Exception {
    PlanningApplicationService service = mock(PlanningApplicationService.class);
    IdentityClient identity = mock(IdentityClient.class);
    UUID owner = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 10, 5);
    LinkedHashMap<LocalDate, Integer> availability = new LinkedHashMap<>();
    availability.put(start, 90);
    PlanRequest request = new PlanRequest(start, availability);
    when(identity.require("Bearer test")).thenReturn(owner);
    when(service.generate(
            eq(owner),
            eq("Bearer test"),
            eq(ZoneId.of("Australia/Sydney")),
            eq(request)))
        .thenReturn(
            new PlanResponse(
                plan, start, start.plusDays(6), 1, List.of(), "Agent validated", Instant.now(), request, 0));
    MockMvc api = MockMvcBuilders.standaloneSetup(new PlanningController(service, identity)).build();

    api.perform(
            post("/api/planning/plans")
                .header("Authorization", "Bearer test")
                .header("X-Study-Timezone", "Australia/Sydney")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startDate":"2026-10-05",
                     "dailyAvailabilityMinutes":{"2026-10-05":90}}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(plan.toString()));

    verify(service)
        .generate(owner, "Bearer test", ZoneId.of("Australia/Sydney"), request);
  }
}
