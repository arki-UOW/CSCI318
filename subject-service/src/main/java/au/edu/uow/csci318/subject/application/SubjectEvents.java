package au.edu.uow.csci318.subject.application;

import au.edu.uow.csci318.messaging.application.EventPublisher;
import au.edu.uow.csci318.messaging.application.SnapshotSource;
import au.edu.uow.csci318.subject.domain.Subject;
import au.edu.uow.csci318.subject.dto.SubjectDtos.SubjectResponse;
import org.springframework.stereotype.Component;

@Component
public class SubjectEvents implements SnapshotSource {
  private final EventPublisher events;
  private final SubjectStore subjects;

  SubjectEvents(EventPublisher events, SubjectStore subjects) {
    this.events = events;
    this.subjects = subjects;
  }

  void publish(String type, Subject subject) {
    events.publish(
        "subjectEvents-out-0",
        type,
        subject.getOwnerId(),
        subject.getId(),
        subject.getEventRevision(),
        new SubjectResponse(
            subject.getId(),
            subject.getCode(),
            subject.getName(),
            subject.getCreditPoints(),
            subject.getWeeklyStudyTargetMinutes()));
  }

  @Override
  public String migrationKey() {
    return "subject-owner-events-v2";
  }

  @Override
  public void enqueueSnapshots() {
    subjects.snapshots().forEach(subject -> publish("SubjectSnapshot", subject));
  }
}
