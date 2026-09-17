USE campus_recycle;
-- 处理方式占比
SELECT process_type,COUNT(*) quantity,ROUND(COUNT(*)*100/(SELECT COUNT(*) FROM items),2) percentage FROM items GROUP BY process_type;
-- 用户环保贡献排行榜
SELECT u.id,u.real_name,COALESCE(SUM(e.change_points),0) total_points FROM users u LEFT JOIN eco_point_records e ON e.user_id=u.id GROUP BY u.id,u.real_name HAVING total_points>0 ORDER BY total_points DESC;
-- 类别循环利用率
SELECT c.name,COUNT(i.id) published, SUM(i.status='COMPLETED') completed, ROUND(SUM(i.status='COMPLETED')*100/NULLIF(COUNT(i.id),0),2) recycle_rate FROM categories c LEFT JOIN items i ON i.category_id=c.id GROUP BY c.id,c.name;
-- 平均处理周期（天）
SELECT ROUND(AVG(TIMESTAMPDIFF(HOUR,published_at,completed_at))/24,2) avg_days FROM items WHERE completed_at IS NOT NULL;
