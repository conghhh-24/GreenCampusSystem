package edu.campus.recycle;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeFeedbackTest extends TestSessionSupport {
    private JdbcTemplate database(long buyer, String status, int existing) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList("SELECT buyer_id,seller_id,status FROM trade_orders WHERE id=? FOR UPDATE", 10L))
            .thenReturn(List.of(Map.of("buyer_id", buyer, "seller_id", 3L, "status", status)));
        when(db.queryForObject("SELECT COUNT(*) FROM trade_feedback WHERE trade_id=?", Integer.class, 10L)).thenReturn(existing);
        when(db.queryForObject(eq("SELECT reputation_score,reputation_bonus_at FROM users WHERE id=? AND status='ACTIVE'"),
            org.mockito.ArgumentMatchers.<RowMapper<Integer>>any(), eq(3L))).thenReturn(100);
        when(db.queryForMap(startsWith("SELECT t.*"), eq(10L))).thenReturn(new HashMap<>(Map.of(
            "id", 10L, "buyer_id", 2L, "status", "REVIEWED", "feedback_id", 1L)));
        return db;
    }

    @ParameterizedTest @CsvSource({"1,-2", "2,-2", "4,2", "5,2"})
    void completedBuyerFeedbackAdjustsSellerReputation(int rating, int delta) {
        JdbcTemplate db = database(2L, "COMPLETED", 0);
        assertEquals("评价已提交", new ApiController(db).feedback(10L,
            Map.of("buyerId", 2L, "rating", rating, "comment", "  交易体验  ")).get("message"));
        verify(db).update("INSERT INTO trade_feedback(trade_id,buyer_id,rating,comment) VALUES(?,?,?,?)", 10L, 2L, rating, "交易体验");
        verify(db).update("UPDATE trade_orders SET status='REVIEWED' WHERE id=?", 10L);
        verify(db).update("UPDATE users SET reputation_score=reputation_score+? WHERE id=?", delta, 3L);
        verify(db).update("INSERT INTO reputation_records(user_id,change_points,reason,source_id) VALUES(?,?,?,?)",
            3L, delta, "买家反馈评分" + rating + "分", 10L);
    }

    @Test void neutralRatingSavesOptionalEmptyCommentWithoutReputationChange() {
        JdbcTemplate db = database(2L, "COMPLETED", 0);
        new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 3));
        verify(db).update("INSERT INTO trade_feedback(trade_id,buyer_id,rating,comment) VALUES(?,?,?,?)", 10L, 2L, 3, "");
        verify(db, never()).update(startsWith("UPDATE users SET reputation_score"), any(Object[].class));
    }

    @Test void duplicateFeedbackCannotAwardReputationAgain() {
        JdbcTemplate db = database(2L, "COMPLETED", 1);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5))).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void reviewedOrderRejectsAnotherFeedback() {
        JdbcTemplate db = database(2L, "REVIEWED", 1);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5))).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void otherUserCannotReviewCompletedOrder() {
        JdbcTemplate db = database(4L, "COMPLETED", 0);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5))).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @ParameterizedTest @ValueSource(strings={"APPLIED", "PROCESSING", "CANCELLED", "REJECTED"})
    void unfinishedOrderCannotBeReviewed(String status) {
        JdbcTemplate db = database(2L, status, 0);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5))).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @ParameterizedTest @ValueSource(strings={"0", "6", "1.5", "invalid", "null"})
    void invalidRatingReturnsBadRequest(String rating) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", rating))).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void oversizedCommentIsRejected() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5, "comment", "好".repeat(256)))).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void missingOrderReturnsNotFound() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> new ApiController(db).feedback(10L, Map.of("buyerId", 2L, "rating", 5))).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }
}
