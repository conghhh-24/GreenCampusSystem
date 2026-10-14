package edu.campus.recycle;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowTest {
    @Test void registerAcceptsMissingOptionalPhone() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        ApiController api = new ApiController(db);
        Map<String,Object> input = new HashMap<>(Map.of("username","test_seller","password","Test123456","realName","测试卖家"));
        input.put("phone",null);
        assertEquals("注册成功",api.register(input).get("message"));
        verify(db).update("INSERT INTO users(username,password_hash,real_name,phone) VALUES(?,?,?,?)","test_seller","Test123456","测试卖家",null);
    }
    @Test void buyerCannotConfirmAsSeller() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForMap("SELECT item_id,buyer_id,seller_id,status FROM trade_orders WHERE id=? FOR UPDATE",1L))
            .thenReturn(Map.of("item_id",10L,"buyer_id",2L,"seller_id",3L,"status","APPLIED"));
        assertThrows(ResponseStatusException.class,()->new ApiController(db).tradeStatus(1L,Map.of("operatorId",2L,"status","PROCESSING")));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void administratorCannotCompleteOtherUsersTrade() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForMap("SELECT item_id,buyer_id,seller_id,status FROM trade_orders WHERE id=? FOR UPDATE",1L))
            .thenReturn(Map.of("item_id",10L,"buyer_id",2L,"seller_id",3L,"status","PROCESSING"));
        assertThrows(ResponseStatusException.class,()->new ApiController(db).tradeStatus(1L,Map.of("operatorId",1L,"status","COMPLETED")));
    }
    @Test void buyerCompletionUpdatesItemAndOrder() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForMap("SELECT item_id,buyer_id,seller_id,status FROM trade_orders WHERE id=? FOR UPDATE",1L))
            .thenReturn(Map.of("item_id",10L,"buyer_id",2L,"seller_id",3L,"status","PROCESSING"));
        new ApiController(db).tradeStatus(1L,Map.of("operatorId",2L,"status","COMPLETED"));
        verify(db).update("UPDATE items SET status='COMPLETED',completed_at=NOW() WHERE id=?",10L);
        verify(db).update("UPDATE trade_orders SET status=?,completed_at=IF(?='COMPLETED',NOW(),completed_at) WHERE id=?","COMPLETED","COMPLETED",1L);
    }
    @Test void completedRecycleCannotBeReopened() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Integer.class,1L)).thenReturn(1);
        when(db.queryForMap("SELECT item_id,status FROM recycling_orders WHERE id=? FOR UPDATE",4L)).thenReturn(Map.of("item_id",10L,"status","COMPLETED"));
        assertThrows(ResponseStatusException.class,()->new ApiController(db).recycleStatus(4L,Map.of("operatorId",1L,"status","APPOINTED")));
    }
    @Test void recycleWithoutCategoryCanCompleteWithZeroRoundedPoints() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Integer.class,1L)).thenReturn(1);
        Map<String,Object> order = new HashMap<>(Map.of("applicant_id",2L,"item_id",10L,"point_factor",1.0));
        order.put("category_id",null);
        when(db.queryForMap("SELECT ro.applicant_id,ro.item_id,i.category_id,COALESCE(c.point_factor,1.00) point_factor FROM recycling_orders ro JOIN items i ON i.id=ro.item_id LEFT JOIN categories c ON c.id=i.category_id WHERE ro.id=? AND ro.status='PROCESSING' FOR UPDATE",4L)).thenReturn(order);
        when(db.queryForObject("SELECT id FROM recycling_records WHERE recycling_order_id=?",Long.class,4L)).thenReturn(9L);
        assertEquals(0,new ApiController(db).complete(4L,Map.of("operatorId",1L,"weightKg",0.1)).get("points"));
        verify(db).update("INSERT INTO recycling_records(recycling_order_id,category_id,weight_kg,recycle_amount,contribution_points) VALUES(?,?,?,?,?)",4L,null,0.1,0,0);
        verify(db,never()).update(eq("INSERT INTO eco_point_records(user_id,recycling_record_id,change_points,reason) VALUES(?,?,?,?)"),any(Object[].class));
    }
}
