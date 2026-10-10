package au.edu.uow.csci318.planning.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CalendarReferencesTest {
  @Test
  void acceptsUnlinkedItemsWithoutRemoteRequests() {
    new RestCalendarReferences(RestClient.builder(), "http://subjects", "http://assessments")
        .verify("Bearer test", null, null);
  }

  @Test
  void requiresMatchingOwnedSubjectAndAssessment() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    UUID subject = UUID.randomUUID(), assessment = UUID.randomUUID();
    var references = new RestCalendarReferences(builder, "http://subjects", "http://assessments");
    server
        .expect(requestTo("http://subjects/api/subjects/" + subject))
        .andExpect(header("Authorization", "Bearer test"))
        .andRespond(withSuccess());
    server
        .expect(requestTo("http://assessments/api/assessments/" + assessment))
        .andExpect(header("Authorization", "Bearer test"))
        .andRespond(
            withSuccess(
                "{\"id\":\"" + assessment + "\",\"subjectId\":\"" + subject + "\"}",
                MediaType.APPLICATION_JSON));
    references.verify("Bearer test", subject, assessment);
    server.verify();
  }

  @Test
  void rejectsMissingForeignAndMismatchedReferences() {
    UUID subject = UUID.randomUUID(), assessment = UUID.randomUUID();
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var references = new RestCalendarReferences(builder, "http://subjects", "http://assessments");
    assertThrows(
        IllegalArgumentException.class, () -> references.verify("Bearer test", null, assessment));
    server
        .expect(requestTo("http://subjects/api/subjects/" + subject))
        .andRespond(withResourceNotFound());
    assertThrows(
        IllegalArgumentException.class,
        () -> references.verify("Bearer test", subject, assessment));
    server.verify();
    server.reset();
    server.expect(requestTo("http://subjects/api/subjects/" + subject)).andRespond(withSuccess());
    server
        .expect(requestTo("http://assessments/api/assessments/" + assessment))
        .andRespond(
            withSuccess(
                "{\"subjectId\":\"" + UUID.randomUUID() + "\"}", MediaType.APPLICATION_JSON));
    assertThrows(
        IllegalArgumentException.class,
        () -> references.verify("Bearer test", subject, assessment));
    server.verify();
  }

  @Test
  void upstreamOutageIsNotReportedAsInvalidInput() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var references = new RestCalendarReferences(builder, "http://subjects", "http://assessments");
    server.expect(anything()).andRespond(withServerError());
    assertThrows(
        RestCalendarReferences.AcademicServiceException.class,
        () -> references.verify("Bearer test", UUID.randomUUID(), null));
    server.verify();
  }
}
