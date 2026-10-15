package edu.campus.recycle;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemEditingTest {
    private static final String LOCK = "SELECT owner_id,status,process_type FROM items WHERE id=? FOR UPDATE";
    private JdbcTemplate database(String status, String type) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(LOCK, 10L)).thenReturn(List.of(Map.of("owner_id", 2L, "status", status, "process_type", type)));
        return db;
    }
    private Map<String,Object> input() {
        return new HashMap<>(Map.of("ownerId", 2L, "name", "教材", "description", "新版描述", "currentPrice", 25,
            "photoUrl", "/api/photos/test.jpg", "meetingDay", "周日", "meetingTime", "21:00", "meetingLocation", "北苑食堂"));
    }
    @ParameterizedTest @ValueSource(strings={"PENDING_REVIEW", "REJECTED", "APPROVED"})
    void editableItemsReturnToReviewWithUpdatedPhotoAndMeeting(String status) {
        JdbcTemplate db = database(status, "SELL");
        assertEquals("修改成功，请等待重新审核", new ApiController(db).update(10L, input()).get("message"));
        verify(db).update("UPDATE items SET name=?,description=?,photo_url=?,current_price=?,meeting_day=?,meeting_time=?,meeting_location=?,status='PENDING_REVIEW',review_reason=NULL,reviewed_by=NULL,reviewed_at=NULL WHERE id=?",
            "教材", "新版描述", "/api/photos/test.jpg", 25.0, "周日", "21:00", "北苑食堂", 10L);
    }
    @ParameterizedTest @ValueSource(strings={"APPLIED", "PROCESSING", "COMPLETED", "OFFLINE"})
    void lockedStatesCannotBeEdited(String status) {
        JdbcTemplate db = database(status, "SELL");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).update(10L, input())).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void otherOwnerCannotEdit() {
        JdbcTemplate db = database("APPROVED", "SELL");
        Map<String,Object> b = input(); b.put("ownerId", 3L);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).update(10L, b)).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void missingItemReturnsNotFound() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(LOCK, 10L)).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).update(10L, input())).getStatusCode());
    }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "NaN", "Infinity", "100000000", "invalid"})
    void invalidSalePriceCannotBeSaved(String price) {
        JdbcTemplate db = database("APPROVED", "SELL");
        Map<String,Object> b = input(); b.put("currentPrice", price);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).update(10L, b)).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void cannotBypassPriceRulesByChangingProcessType() {
        JdbcTemplate db = database("APPROVED", "GIVE");
        Map<String,Object> b = input(); b.put("processType", "SELL");
        assertThrows(ResponseStatusException.class, () -> new ApiController(db).update(10L, b));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void invalidMeetingCannotBeSaved() {
        JdbcTemplate db = database("APPROVED", "SELL");
        Map<String,Object> b = input(); b.put("meetingTime", "22:00");
        assertThrows(ResponseStatusException.class, () -> new ApiController(db).update(10L, b));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void optionalPhotoCanBeRemoved() {
        JdbcTemplate db = database("REJECTED", "GIVE");
        Map<String,Object> b = input(); b.put("currentPrice", 0); b.put("photoUrl", "");
        new ApiController(db).update(10L, b);
        verify(db).update(anyString(), eq("教材"), eq("新版描述"), eq(""), eq(0.0), eq("周日"), eq("21:00"), eq("北苑食堂"), eq(10L));
    }
}
