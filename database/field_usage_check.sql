-- 只读：检查精简字段是否仍存在，以及保留字段的数据分布。
USE campus_recycle;

-- 完成精简后返回0行；删除前可用于确认目标字段。
SELECT TABLE_NAME,COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE()
  AND ((TABLE_NAME='items' AND COLUMN_NAME IN ('original_price','estimated_recycle_price','condition_level'))
    OR (TABLE_NAME='recycling_records' AND COLUMN_NAME='recycle_amount')
    OR (TABLE_NAME='recycling_stations' AND COLUMN_NAME IN ('phone','accepted_categories')));

-- 以下为正常数据分布，不要求结果为0。
SELECT COUNT(*) AS total_items,
       COALESCE(SUM(category_id IS NULL),0) AS uncategorized_items
FROM items;

SELECT c.id,c.name,c.point_factor,COUNT(i.id) AS item_count
FROM categories c LEFT JOIN items i ON i.category_id=c.id
GROUP BY c.id,c.name,c.point_factor ORDER BY c.id;

SELECT id,name,process_type,status,category_id,current_price
FROM items ORDER BY id DESC LIMIT 30;

SELECT COUNT(*) AS recycling_record_count,
       COALESCE(SUM(category_id IS NULL),0) AS without_category,
       COALESCE(SUM(weight_kg),0) AS total_weight,
       COALESCE(SUM(contribution_points),0) AS total_awarded_points
FROM recycling_records;

SELECT id,name,location,status FROM recycling_stations ORDER BY id;
SELECT COUNT(*) AS feedback_count FROM trade_feedback;
SELECT id,trade_id,buyer_id,rating,comment,created_at
FROM trade_feedback ORDER BY created_at DESC LIMIT 30;
