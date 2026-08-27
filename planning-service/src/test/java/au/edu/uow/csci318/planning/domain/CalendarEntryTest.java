package au.edu.uow.csci318.planning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static au.edu.uow.csci318.planning.domain.CalendarEntry.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarEntryTest {
    @Test
    void validatesTimesAndCompletesOnce() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 24, 18, 0);
        assertThrows(IllegalArgumentException.class, () -> new CalendarEntry(UUID.randomUUID(), null,
                null, null, null, "Study", "", EntryType.STUDY_SESSION, start, start,
                Origin.MANUAL, true, 0));
        CalendarEntry entry = new CalendarEntry(UUID.randomUUID(), null, null, null, null,
                "Study", "", EntryType.STUDY_SESSION, start, start.plusHours(1),
                Origin.MANUAL, true, 0);
        entry.complete();
        assertEquals(EntryStatus.COMPLETED, entry.getStatus());
        assertThrows(IllegalStateException.class, entry::complete);
    }
}
