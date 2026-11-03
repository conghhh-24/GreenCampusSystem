package edu.campus.recycle;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecyclingAppointmentTest extends TestSessionSupport {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T02:00:00Z"), RecyclingAppointment.ZONE);
    @ParameterizedTest @ValueSource(strings={"2026-11-03 08:00:00","2026-11-02 10:30:00","2026-11-03 21:00:00"})
    void acceptsFutureBoundarySlots(String value) { assertNotNull(RecyclingAppointment.validate(value,clock)); }
    @ParameterizedTest @NullSource @ValueSource(strings={"", "2026-11-02 09:30:00", "2026-11-02 10:00:00",
        "2026-11-03 07:30:00", "2026-11-03 21:30:00", "2026-11-03 12:15:00", "2026-11-03 12:30:01",
        "2027-02-29 12:00:00", "2026-13-02 12:00:00", "2026-11-03T12:00:00", "not-a-date"})
    void rejectsInvalidSlots(String value) { assertThrows(ResponseStatusException.class, () -> RecyclingAppointment.validate(value,clock)); }
    @Test void rejectedAppointmentNeverReachesDatabase() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertThrows(ResponseStatusException.class, () -> new ApiController(db).recycle(Map.of("applicantId",2L,"appointmentAt","2000-01-01 08:00:00")));
        verifyNoInteractions(db);
    }
}
