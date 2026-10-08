package au.edu.uow.csci318.planning.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestPlanningDataTest {
  @Test
  void authoritativeIncompleteAssessmentsAreNotFilteredByTitleHeuristics() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://assessment/api/assessments"))
        .andExpect(header("Authorization", "Bearer test"))
        .andRespond(
            withSuccess(
                """
                [{"title":"AI","status":"INCOMPLETE"},
                 {"title":"Public Policy Essay","status":"INCOMPLETE"},
                 {"title":"Academic Integrity Analysis","status":"INCOMPLETE"},
                 {"title":"Completed Report","status":"COMPLETED"}]
                """,
                MediaType.APPLICATION_JSON));
    var result =
        new RestPlanningData(builder, "http://assessment", "http://subject", "http://activity")
            .getIncompleteAssessments("Bearer test");
    assertEquals(
        java.util.List.of("AI", "Public Policy Essay", "Academic Integrity Analysis"),
        result.stream().map(item -> item.title()).toList());
    server.verify();
  }
}
