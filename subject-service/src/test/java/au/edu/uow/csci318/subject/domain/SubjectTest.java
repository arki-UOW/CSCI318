package au.edu.uow.csci318.subject.domain;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SubjectTest {
 @Test void rejectsInvalidCode(){assertThrows(IllegalArgumentException.class,()->new Subject(UUID.randomUUID(),"bad","Name",6,120));}
 @Test void changesTargetThroughInvariant(){var s=new Subject(UUID.randomUUID(),"CSCI318","Software Engineering",6,120);s.changeWeeklyStudyTarget(240);assertEquals(240,s.getWeeklyStudyTargetMinutes());assertThrows(IllegalArgumentException.class,()->s.changeWeeklyStudyTarget(-1));}
}
