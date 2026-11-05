package edu.campus.recycle;

import org.springframework.jdbc.core.JdbcTemplate;

final class TradeFeedbackSchema {
    private TradeFeedbackSchema() {}

    static void migrate(JdbcTemplate db) {
        String type = db.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_orders' AND COLUMN_NAME='status'", String.class);
        if (type == null || !type.contains("'REVIEWED'")) {
            db.execute("ALTER TABLE trade_orders MODIFY status ENUM('APPLIED','PROCESSING','COMPLETED','REVIEWED','CANCELLED','REJECTED') NOT NULL DEFAULT 'APPLIED'");
        }
        // Repair legacy completed orders without replaying feedback or reputation changes.
        db.update("UPDATE trade_orders SET status='REVIEWED' WHERE status='COMPLETED' AND EXISTS (SELECT 1 FROM trade_feedback f WHERE f.trade_id=trade_orders.id)");
    }
}
