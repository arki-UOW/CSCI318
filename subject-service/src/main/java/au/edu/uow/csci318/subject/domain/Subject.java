package au.edu.uow.csci318.subject.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="subjects", uniqueConstraints=@UniqueConstraint(columnNames="code"))
public class Subject {
    @Id private UUID id;
    @Column(nullable=false) private String code;
    @Column(nullable=false) private String name;
    private Integer creditPoints;
    @Column(nullable=false) private int weeklyStudyTargetMinutes;
    protected Subject() {}
    public Subject(String code, String name, Integer creditPoints, int target) {
        this.id=UUID.randomUUID(); this.code=normaliseCode(code); this.name=requireText(name,"Subject name");
        if (creditPoints != null && creditPoints <= 0) throw new IllegalArgumentException("Credit points must be positive");
        this.creditPoints=creditPoints; changeWeeklyStudyTarget(target);
    }
    private static String normaliseCode(String value) { String v=requireText(value,"Subject code").toUpperCase(); if(!v.matches("[A-Z]{2,8}[0-9]{3,4}")) throw new IllegalArgumentException("Subject code must look like CSCI318"); return v; }
    private static String requireText(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");return value.trim();}
    public void changeWeeklyStudyTarget(int minutes){if(minutes<0||minutes>10080)throw new IllegalArgumentException("Weekly target must be between 0 and 10080 minutes");weeklyStudyTargetMinutes=minutes;}
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public Integer getCreditPoints(){return creditPoints;} public int getWeeklyStudyTargetMinutes(){return weeklyStudyTargetMinutes;}
}
