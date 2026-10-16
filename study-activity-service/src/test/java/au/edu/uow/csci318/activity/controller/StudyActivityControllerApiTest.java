package au.edu.uow.csci318.activity.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import au.edu.uow.csci318.activity.application.StudyActivityApplicationService;
import au.edu.uow.csci318.activity.application.StudyActivityApplicationService.CreateRequest;
import au.edu.uow.csci318.activity.application.StudyActivityApplicationService.Response;
import au.edu.uow.csci318.activity.infrastructure.IdentityClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudyActivityControllerApiTest {
  @Test
  void recordsSessionUsingExplicitUserTimezone() throws Exception {
    StudyActivityApplicationService service = mock(StudyActivityApplicationService.class);
    IdentityClient identity = mock(IdentityClient.class);
    UUID owner = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 7);
    CreateRequest request = new CreateRequest(subject, 45, date, "Review");
    when(identity.require("Bearer test")).thenReturn(owner);
    when(service.record(
            eq(owner), eq("Bearer test"), eq(request), eq(ZoneId.of("Australia/Sydney"))))
        .thenReturn(new Response(session, subject, 45, date, "Review", Instant.now(), null));
    MockMvc api =
        MockMvcBuilders.standaloneSetup(new StudyActivityController(service, identity)).build();

    api.perform(
            post("/api/study-sessions")
                .header("Authorization", "Bearer test")
                .header("X-Study-Timezone", "Australia/Sydney")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"subjectId":"%s","durationMinutes":45,
                     "studyDate":"2026-10-07","description":"Review"}
                    """
                        .formatted(subject)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationMinutes").value(45));

    verify(service)
        .record(owner, "Bearer test", request, ZoneId.of("Australia/Sydney"));
  }

  @Test
  void filtersSessionHistoryBySelectedSubject() throws Exception {
    StudyActivityApplicationService service = mock(StudyActivityApplicationService.class);
    IdentityClient identity = mock(IdentityClient.class);
    UUID owner = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    when(identity.require("Bearer test")).thenReturn(owner);
    when(service.all(owner, subject)).thenReturn(List.of());
    MockMvc api =
        MockMvcBuilders.standaloneSetup(new StudyActivityController(service, identity)).build();

    api.perform(
            get("/api/study-sessions")
                .header("Authorization", "Bearer test")
                .queryParam("subjectId", subject.toString()))
        .andExpect(status().isOk());

    verify(service).all(owner, subject);
  }
}
