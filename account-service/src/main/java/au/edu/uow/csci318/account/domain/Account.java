package au.edu.uow.csci318.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class Account {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 80) private String displayName;
    private String institution;
    private String course;
    @Column(length = 800) private String studyGoal;
    @Column(nullable = false, length = 80) private String timezone;
    @Column(nullable = false, length = 7) private String primaryColor;
    @Column(nullable = false, length = 7) private String accentColor;
    @Column(nullable = false, length = 7) private String backgroundColor;
    @Column(nullable = false, length = 7) private String surfaceColor;
    @Column(nullable = false, length = 7) private String textColor;
    @Column(nullable = false) private Instant createdAt;

    protected Account() {}

    public Account(String username, String passwordHash, String displayName, String timezone) {
        this.id = UUID.randomUUID();
        this.username = normaliseUsername(username);
        this.passwordHash = require(passwordHash, "Password hash");
        this.displayName = require(displayName, "Display name");
        this.timezone = require(timezone, "Timezone");
        this.primaryColor = "#245d45";
        this.accentColor = "#d69b38";
        this.backgroundColor = "#f5f7f5";
        this.surfaceColor = "#ffffff";
        this.textColor = "#17201d";
        this.createdAt = Instant.now();
    }

    public void updateProfile(String displayName, String institution, String course, String studyGoal, String timezone) {
        this.displayName = require(displayName, "Display name");
        this.institution = clean(institution);
        this.course = clean(course);
        this.studyGoal = clean(studyGoal);
        this.timezone = require(timezone, "Timezone");
    }

    public void updateTheme(String primary, String accent, String background, String surface, String text) {
        this.primaryColor = colour(primary, "Primary colour");
        this.accentColor = colour(accent, "Accent colour");
        this.backgroundColor = colour(background, "Background colour");
        this.surfaceColor = colour(surface, "Surface colour");
        this.textColor = colour(text, "Text colour");
    }

    public static String normaliseUsername(String value) {
        String username = require(value, "Username").toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,31}")) {
            throw new IllegalArgumentException("Username must be 3-32 characters using letters, numbers, dots, dashes or underscores");
        }
        return username;
    }

    private static String colour(String value, String label) {
        String colour = require(value, label).toLowerCase(Locale.ROOT);
        if (!colour.matches("#[0-9a-f]{6}")) throw new IllegalArgumentException(label + " must be a six-digit hex colour");
        return colour;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getInstitution() { return institution; }
    public String getCourse() { return course; }
    public String getStudyGoal() { return studyGoal; }
    public String getTimezone() { return timezone; }
    public String getPrimaryColor() { return primaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getSurfaceColor() { return surfaceColor; }
    public String getTextColor() { return textColor; }
    public Instant getCreatedAt() { return createdAt; }
}
