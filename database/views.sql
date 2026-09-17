USE campus_recycle;
CREATE OR REPLACE VIEW v_monthly_item_stats AS SELECT DATE_FORMAT(published_at,'%Y-%m') month, COUNT(*) total_items, SUM(status='COMPLETED') completed_items FROM items GROUP BY DATE_FORMAT(published_at,'%Y-%m');
CREATE OR REPLACE VIEW v_category_stats AS SELECT c.id,c.name,COUNT(i.id) item_count,ROUND(AVG(NULLIF(i.current_price,0)),2) avg_price FROM categories c LEFT JOIN items i ON i.category_id=c.id GROUP BY c.id,c.name;
CREATE OR REPLACE VIEW v_station_recycle_stats AS SELECT s.id,s.name,COUNT(rr.id) record_count,COALESCE(SUM(rr.weight_kg),0) total_weight FROM recycling_stations s LEFT JOIN recycling_orders ro ON ro.station_id=s.id AND ro.status='COMPLETED' LEFT JOIN recycling_records rr ON rr.recycling_order_id=ro.id GROUP BY s.id,s.name;
