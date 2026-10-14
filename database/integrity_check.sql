USE campus_recycle;
-- Read-only structure and relationship checks. Every anomaly count should be 0.
SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,COLUMN_NAME;
SELECT 'orphan_items' issue,COUNT(*) anomalies FROM items i LEFT JOIN users u ON u.id=i.owner_id WHERE u.id IS NULL
UNION ALL SELECT 'trade_parties',COUNT(*) FROM trade_orders t JOIN items i ON i.id=t.item_id WHERE t.seller_id<>i.owner_id OR t.buyer_id=t.seller_id OR t.process_type<>i.process_type
UNION ALL SELECT 'recycle_owner_type',COUNT(*) FROM recycling_orders r JOIN items i ON i.id=r.item_id WHERE r.applicant_id<>i.owner_id OR i.process_type<>'RECYCLE'
UNION ALL SELECT 'completed_trade_item',COUNT(*) FROM trade_orders t JOIN items i ON i.id=t.item_id WHERE t.status='COMPLETED' AND (i.status<>'COMPLETED' OR t.completed_at IS NULL)
UNION ALL SELECT 'completed_recycle_record',COUNT(*) FROM recycling_orders o LEFT JOIN recycling_records r ON r.recycling_order_id=o.id WHERE o.status='COMPLETED' AND (r.id IS NULL OR o.completed_at IS NULL)
UNION ALL SELECT 'record_order_status',COUNT(*) FROM recycling_records r JOIN recycling_orders o ON o.id=r.recycling_order_id WHERE o.status<>'COMPLETED'
UNION ALL SELECT 'eco_points_total',COUNT(*) FROM users u LEFT JOIN (SELECT user_id,SUM(change_points) points FROM eco_point_records GROUP BY user_id) p ON p.user_id=u.id WHERE u.total_points<>COALESCE(p.points,0)
UNION ALL SELECT 'recycle_points_owner',COUNT(*) FROM eco_point_records e JOIN recycling_records r ON r.id=e.recycling_record_id JOIN recycling_orders o ON o.id=r.recycling_order_id WHERE e.user_id<>o.applicant_id
UNION ALL SELECT 'recycle_points_amount',COUNT(*) FROM recycling_records r LEFT JOIN (SELECT recycling_record_id,SUM(change_points) points FROM eco_point_records GROUP BY recycling_record_id) p ON p.recycling_record_id=r.id WHERE r.contribution_points<>COALESCE(p.points,0)
UNION ALL SELECT 'feedback_buyer',COUNT(*) FROM trade_feedback f JOIN trade_orders t ON t.id=f.trade_id WHERE f.buyer_id<>t.buyer_id OR t.status<>'COMPLETED' OR f.rating NOT BETWEEN 1 AND 5
UNION ALL SELECT 'duplicate_active_trade',COUNT(*) FROM (SELECT item_id FROM trade_orders WHERE status IN ('APPLIED','PROCESSING') GROUP BY item_id HAVING COUNT(*)>1) duplicates;
SELECT 'occupied_item_without_order' issue,COUNT(*) anomalies FROM items i WHERE i.status IN ('APPLIED','PROCESSING') AND NOT EXISTS (SELECT 1 FROM trade_orders t WHERE t.item_id=i.id AND t.status IN ('APPLIED','PROCESSING')) AND NOT EXISTS (SELECT 1 FROM recycling_orders r WHERE r.item_id=i.id AND r.status IN ('APPLIED','APPOINTED','PROCESSING'));
