package au.edu.uow.csci318.assessment.domain;
import jakarta.persistence.*;import java.time.*;import java.util.*;
@Entity @Table(name="assessments",uniqueConstraints=@UniqueConstraint(columnNames={"subjectId","title"}))
public class Assessment{
 public enum Status{INCOMPLETE,COMPLETED} public enum Priority{LOW,MEDIUM,HIGH}
 @Id private UUID id;@Column(nullable=false)private UUID subjectId;@Column(nullable=false)private String title;private String type;private Double weighting;private LocalDate dueDate;private Integer dueWeek;private String description;private Integer estimatedMinutes;@Enumerated(EnumType.STRING)private Priority priority;@Enumerated(EnumType.STRING)private Status status;@Column(nullable=false)private Instant updatedAt;
 protected Assessment(){}
 public Assessment(UUID subjectId,String title,String type,Double weighting,LocalDate dueDate,Integer dueWeek,String description,Integer estimatedMinutes,Priority priority){id=UUID.randomUUID();this.subjectId=Objects.requireNonNull(subjectId);this.title=require(title);this.type=type;changeWeighting(weighting);changeDeadline(dueDate,dueWeek);this.description=description;changeEstimatedWorkload(estimatedMinutes);this.priority=priority==null?Priority.MEDIUM:priority;status=Status.INCOMPLETE;updatedAt=Instant.now();}
 public void changeDeadline(LocalDate date,Integer week){if(date!=null&&date.isBefore(LocalDate.of(2000,1,1)))throw new IllegalArgumentException("Due date is invalid");if(week!=null&&(week<1||week>20))throw new IllegalArgumentException("Due week must be between 1 and 20");dueDate=date;dueWeek=week;touch();}
 public void changeWeighting(Double value){if(value!=null&&(value<0||value>100))throw new IllegalArgumentException("Weighting must be between 0 and 100");weighting=value;touch();}
 public void changeEstimatedWorkload(Integer minutes){if(minutes!=null&&minutes<0)throw new IllegalArgumentException("Estimated workload cannot be negative");estimatedMinutes=minutes;touch();}
 public void changePriority(Priority value){priority=Objects.requireNonNull(value);touch();}
 public void markCompleted(){if(status==Status.COMPLETED)throw new IllegalStateException("Assessment is already completed");status=Status.COMPLETED;touch();}
 private void touch(){updatedAt=Instant.now();}private static String require(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Assessment title is required");return v.trim();}
 public UUID getId(){return id;}public UUID getSubjectId(){return subjectId;}public String getTitle(){return title;}public String getType(){return type;}public Double getWeighting(){return weighting;}public LocalDate getDueDate(){return dueDate;}public Integer getDueWeek(){return dueWeek;}public String getDescription(){return description;}public Integer getEstimatedMinutes(){return estimatedMinutes;}public Priority getPriority(){return priority;}public Status getStatus(){return status;}public Instant getUpdatedAt(){return updatedAt;}
}
