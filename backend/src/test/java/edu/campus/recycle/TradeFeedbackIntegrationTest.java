package edu.campus.recycle;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TradeFeedbackIntegrationTest extends TestSessionSupport {
    private JdbcTemplate db;
    private ApiController api;
    private MockMvc mvc;

    @BeforeEach void database() {
        DriverManagerDataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,real_name VARCHAR(50),role VARCHAR(10),status VARCHAR(10),reputation_score INT DEFAULT 100,reputation_bonus_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE items(id BIGINT PRIMARY KEY,name VARCHAR(100),status VARCHAR(30))");
        db.execute("CREATE TABLE trade_orders(id BIGINT PRIMARY KEY,item_id BIGINT,buyer_id BIGINT,seller_id BIGINT,status ENUM('APPLIED','PROCESSING','COMPLETED','REVIEWED','CANCELLED','REJECTED'),applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE trade_feedback(id BIGINT AUTO_INCREMENT PRIMARY KEY,trade_id BIGINT NOT NULL UNIQUE REFERENCES trade_orders(id),buyer_id BIGINT NOT NULL REFERENCES users(id),rating TINYINT CHECK(rating BETWEEN 1 AND 5),comment VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE reputation_records(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT REFERENCES users(id),change_points INT,reason VARCHAR(200),source_id BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.update("INSERT INTO users(id,real_name,role,status) VALUES(1,'管理员','ADMIN','ACTIVE'),(2,'买家','USER','ACTIVE'),(3,'卖家','USER','ACTIVE')");
        db.update("INSERT INTO items VALUES(20,'教材','COMPLETED')");
        db.update("INSERT INTO trade_orders(id,item_id,buyer_id,seller_id,status) VALUES(10,20,2,3,'COMPLETED')");
        // Use the production @Transactional annotations with a real database transaction manager.
        ProxyFactory factory = new ProxyFactory(new ApiController(db));
        factory.setProxyTargetClass(true);
        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(new DataSourceTransactionManager(source));
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(transactions);
        api = (ApiController) factory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(api).setControllerAdvice(new ApiErrors()).build();
    }

    @AfterEach void shutdown() { db.execute("SHUTDOWN"); }

    private ResultActions submit(int rating, String text) throws Exception {
        return mvc.perform(post("/api/trades/10/feedback").requestAttr("authenticatedUserId", 2L)
            .requestAttr("authenticatedRole", "USER").contentType("application/json")
            .content("{\"buyerId\":2,\"rating\":" + rating + ",\"comment\":\"" + text + "\"}"));
    }

    private ResultActions orders(long userId, boolean admin) throws Exception {
        return mvc.perform(get("/api/trades").param("userId", String.valueOf(userId)).param("admin", String.valueOf(admin))
            .requestAttr("authenticatedUserId", userId).requestAttr("authenticatedRole", admin ? "ADMIN" : "USER"));
    }

    @Test void submitThenReloadReturnsReviewedStateAndContentForAllParticipants() throws Exception {
        orders(2L, false).andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].can_feedback").value(true));
        submit(5, "物品很好，交易顺利").andExpect(status().isOk())
            .andExpect(jsonPath("$.trade.status").value("REVIEWED"))
            .andExpect(jsonPath("$.trade.feedback_submitted").value(true))
            .andExpect(jsonPath("$.trade.can_feedback").value(false))
            .andExpect(jsonPath("$.trade.feedback_rating").value(5))
            .andExpect(jsonPath("$.trade.feedback_comment").value("物品很好，交易顺利"));
        assertEquals("REVIEWED", db.queryForObject("SELECT status FROM trade_orders WHERE id=10", String.class));
        for (long userId : new long[]{2L, 3L, 1L}) {
            orders(userId, userId == 1L).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVIEWED"))
                .andExpect(jsonPath("$[0].can_feedback").value(false))
                .andExpect(jsonPath("$[0].feedback_rating").value(5))
                .andExpect(jsonPath("$[0].feedback_comment").value("物品很好，交易顺利"));
        }
        submit(1, "重复评价").andExpect(status().isConflict());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM trade_feedback", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM reputation_records", Integer.class));
        assertEquals(102, db.queryForObject("SELECT reputation_score FROM users WHERE id=3", Integer.class));
    }

    @Test void neutralFeedbackStillCompletesReviewWithoutReputationChange() throws Exception {
        submit(3, "").andExpect(status().isOk()).andExpect(jsonPath("$.trade.status").value("REVIEWED"))
            .andExpect(jsonPath("$.trade.feedback_comment").value(""));
        orders(2L, false).andExpect(jsonPath("$[0].can_feedback").value(false));
        assertEquals(100, db.queryForObject("SELECT reputation_score FROM users WHERE id=3", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM reputation_records", Integer.class));
    }

    @Test void failureDuringReputationWriteRollsBackFeedbackAndStatus() {
        db.execute("ALTER TABLE reputation_records ADD CHECK(change_points=0)");
        signedIn(2L);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> api.feedback(10L, Map.of("buyerId", 2L, "rating", 5)));
        assertEquals("COMPLETED", db.queryForObject("SELECT status FROM trade_orders WHERE id=10", String.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM trade_feedback", Integer.class));
        assertEquals(100, db.queryForObject("SELECT reputation_score FROM users WHERE id=3", Integer.class));
    }

    @Test void concurrentFeedbackOnlyCommitsOneReviewAndOneReputationChange() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> task = () -> {
            signedIn(2L);
            try {
                start.await(5, TimeUnit.SECONDS);
                api.feedback(10L, Map.of("buyerId", 2L, "rating", 5));
                return 200;
            } catch (ResponseStatusException error) { return error.getStatusCode().value(); }
            finally { clearRequest(); }
        };
        try {
            Future<Integer> first = pool.submit(task), second = pool.submit(task);
            assertEquals(Set.of(200, 409), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM trade_feedback", Integer.class));
            assertEquals(102, db.queryForObject("SELECT reputation_score FROM users WHERE id=3", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void migrationRepairsLegacyFeedbackWithoutAwardingPointsAgain() throws Exception {
        db.execute("ALTER TABLE trade_orders MODIFY status ENUM('APPLIED','PROCESSING','COMPLETED','CANCELLED','REJECTED') NOT NULL DEFAULT 'APPLIED'");
        db.update("INSERT INTO trade_feedback(trade_id,buyer_id,rating,comment) VALUES(10,2,5,'旧评价')");
        JdbcTemplate migrationDb = spy(db);
        doReturn("enum('APPLIED','PROCESSING','COMPLETED','CANCELLED','REJECTED')",
            "enum('APPLIED','PROCESSING','COMPLETED','REVIEWED','CANCELLED','REJECTED')").when(migrationDb)
            .queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_orders' AND COLUMN_NAME='status'", String.class);
        TradeFeedbackSchema.migrate(migrationDb);
        TradeFeedbackSchema.migrate(migrationDb);
        assertEquals("REVIEWED", db.queryForObject("SELECT status FROM trade_orders WHERE id=10", String.class));
        orders(2L, false).andExpect(jsonPath("$[0].feedback_comment").value("旧评价"))
            .andExpect(jsonPath("$[0].can_feedback").value(false));
        assertEquals(100, db.queryForObject("SELECT reputation_score FROM users WHERE id=3", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM reputation_records", Integer.class));
    }
}
