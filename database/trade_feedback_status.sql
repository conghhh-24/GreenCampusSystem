USE campus_recycle;
-- Optional manual migration. The updated backend also performs this on startup.
ALTER TABLE trade_orders MODIFY status
 ENUM('APPLIED','PROCESSING','COMPLETED','REVIEWED','CANCELLED','REJECTED')
 NOT NULL DEFAULT 'APPLIED';
UPDATE trade_orders SET status='REVIEWED'
 WHERE status='COMPLETED'
 AND EXISTS (SELECT 1 FROM trade_feedback f WHERE f.trade_id=trade_orders.id);
