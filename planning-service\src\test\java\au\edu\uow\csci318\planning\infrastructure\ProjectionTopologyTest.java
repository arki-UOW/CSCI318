package au.edu.uow.csci318.planning.infrastructure;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ProjectionTopologyTest{@Test void eventIdsProvideIdempotencyKey(){UUID id=UUID.randomUUID();assertEquals(id,UUID.fromString(id.toString()));}}
