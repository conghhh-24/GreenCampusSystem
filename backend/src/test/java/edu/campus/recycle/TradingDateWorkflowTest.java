package edu.campus.recycle;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradingDateWorkflowTest extends TestSessionSupport {
    private static final String LOCK = "SELECT owner_id,process_type,current_price,status,meeting_date,meeting_time,meeting_location FROM items WHERE id=? FOR UPDATE";
    private JdbcTemplate database(Object date) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(eq("SELECT reputation_score,reputation_bonus_at FROM users WHERE id=? AND status='ACTIVE'"), any(RowMapper.class), eq(2L))).thenReturn(100);
        Map<String,Object> item = new HashMap<>(Map.of("owner_id",3L,"process_type","SELL","current_price",20,"status","APPROVED",
            "meeting_time","14:00","meeting_location","南苑食堂"));
        item.put("meeting_date",date);
        when(db.queryForMap(LOCK,10L)).thenReturn(item);
        return db;
    }
    @Test void orderSnapshotsTheSpecificDateFromDatabaseRatherThanRequest() {
        var date = java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        JdbcTemplate db=database(date);
        new ApiController(db).trade(Map.of("itemId",10L,"buyerId",2L,"meetingDate","2099-12-31","amount",0));
        verify(db).update("INSERT INTO trade_orders(item_id,buyer_id,seller_id,process_type,amount,meeting_date,meeting_time,meeting_location) VALUES(?,?,?,?,?,?,?,?)",
            10L,2L,3L,"SELL",20,date,"14:00","南苑食堂");
    }
    @Test void expiredAndLegacyListingsCannotGenerateOrders() {
        for (Object date : new Object[]{null,java.sql.Date.valueOf("2000-01-01")}) {
            JdbcTemplate db=database(date);
            assertThrows(ResponseStatusException.class,()->new ApiController(db).trade(Map.of("itemId",10L,"buyerId",2L)));
            verify(db,never()).update(anyString(),any(Object[].class));
        }
    }
}
