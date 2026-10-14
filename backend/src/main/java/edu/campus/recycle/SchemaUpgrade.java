package edu.campus.recycle;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Upgrade existing installations without rebuilding tables or deleting history. */
@Component
public class SchemaUpgrade {
    private final JdbcTemplate db;
    public SchemaUpgrade(JdbcTemplate db) { this.db = db; }
    private void add(String table, String column, String definition) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?", Integer.class, table, column);
        if (count == 0) db.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
    @PostConstruct
    public void upgrade() {
        for (String table : new String[]{"items", "trade_orders"}) {
            add(table, "meeting_day", "VARCHAR(8) NULL");
            add(table, "meeting_time", "VARCHAR(5) NULL");
            add(table, "meeting_location", "VARCHAR(30) NULL");
        }
        add("items", "review_reason", "VARCHAR(255) NULL");
        add("items", "reviewed_by", "BIGINT NULL");
        add("items", "reviewed_at", "DATETIME NULL");
        db.execute("ALTER TABLE items MODIFY status ENUM('PENDING_REVIEW','APPROVED','REJECTED','PENDING','APPLIED','PROCESSING','COMPLETED','OFFLINE','CANCELLED') NOT NULL DEFAULT 'PENDING_REVIEW'");
        db.update("UPDATE items SET status='PENDING_REVIEW' WHERE status='PENDING'");
        // Old cancellations could leave an item occupied without a live order.
        // Review it before relisting, since legacy items may never have been reviewed.
        db.update("UPDATE items i SET status='PENDING_REVIEW' WHERE i.status IN ('APPLIED','PROCESSING') AND NOT EXISTS (SELECT 1 FROM trade_orders t WHERE t.item_id=i.id AND t.status IN ('APPLIED','PROCESSING','COMPLETED')) AND NOT EXISTS (SELECT 1 FROM recycling_orders r WHERE r.item_id=i.id AND r.status IN ('APPLIED','APPOINTED','PROCESSING','COMPLETED'))");
        db.execute("ALTER TABLE recycling_records MODIFY category_id BIGINT NULL");
        db.execute("ALTER TABLE recycling_records MODIFY contribution_points INT NOT NULL");
        // Zero is a valid result when a small positive weight rounds to zero points.
        var checks = db.queryForList("SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='recycling_records' AND tc.CONSTRAINT_TYPE='CHECK'");
        for (var check : checks) {
            String clause = String.valueOf(check.get("CHECK_CLAUSE"));
            if (clause.contains("contribution_points") && !clause.contains(">="))
                db.execute("ALTER TABLE recycling_records DROP CHECK `" + check.get("CONSTRAINT_NAME") + "`");
        }
        Integer count = db.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recycling_records' AND CONSTRAINT_NAME='chk_recycle_points_nonnegative'", Integer.class);
        if (count == 0) db.execute("ALTER TABLE recycling_records ADD CONSTRAINT chk_recycle_points_nonnegative CHECK (contribution_points >= 0)");
        Integer reviewerKey = db.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='items' AND COLUMN_NAME='reviewed_by' AND REFERENCED_TABLE_NAME='users'", Integer.class);
        if (reviewerKey == 0) db.execute("ALTER TABLE items ADD CONSTRAINT fk_item_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id)");
        db.execute("CREATE OR REPLACE VIEW v_category_stats AS SELECT c.id,c.name,COUNT(i.id) item_count,ROUND(AVG(NULLIF(i.current_price,0)),2) avg_price FROM categories c LEFT JOIN items i ON i.category_id=c.id GROUP BY c.id,c.name UNION ALL SELECT NULL,'未分类',COUNT(*),ROUND(AVG(NULLIF(current_price,0)),2) FROM items WHERE category_id IS NULL");
    }
}
