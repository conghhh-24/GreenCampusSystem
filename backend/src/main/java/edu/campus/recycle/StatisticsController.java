package edu.campus.recycle;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
public class StatisticsController {
    private final JdbcTemplate db;
    public StatisticsController(JdbcTemplate db) { this.db = db; }
    @GetMapping("/overview")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String,Object> overview() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("summary", db.queryForMap("""
            SELECT COUNT(*) total_items, COALESCE(SUM(status='COMPLETED'),0) completed_items,
              COALESCE(ROUND(SUM(status='COMPLETED')*100.0/NULLIF(COUNT(*),0),2),0) completion_rate,
              ROUND(AVG(CASE WHEN status='COMPLETED' AND completed_at>=published_at
                THEN TIMESTAMPDIFF(SECOND,published_at,completed_at)/86400.0 END),2) avg_processing_days,
              (SELECT COALESCE(SUM(rr.weight_kg),0) FROM recycling_records rr
                JOIN recycling_orders ro ON ro.id=rr.recycling_order_id WHERE ro.status='COMPLETED') total_recycle_weight,
              (SELECT COUNT(*) FROM recycling_records rr JOIN recycling_orders ro
                ON ro.id=rr.recycling_order_id WHERE ro.status='COMPLETED') completed_recycles
            FROM items
            """));
        result.put("methods", db.queryForList("""
            SELECT process_type,COUNT(*) quantity,
              ROUND(COUNT(*)*100.0/NULLIF((SELECT COUNT(*) FROM items),0),2) percentage
            FROM items GROUP BY process_type ORDER BY process_type
            """));
        result.put("stations", db.queryForList("SELECT * FROM v_station_recycle_stats ORDER BY total_weight DESC,id"));
        result.put("categories", db.queryForList("SELECT * FROM v_category_stats"));
        result.put("ranking", db.queryForList("SELECT u.real_name,SUM(e.change_points) total_points FROM users u JOIN eco_point_records e ON e.user_id=u.id GROUP BY u.id,u.real_name ORDER BY total_points DESC,u.id"));
        YearMonth current = YearMonth.now(RecyclingAppointment.ZONE);
        LocalDateTime start = current.minusMonths(11).atDay(1).atStartOfDay();
        LocalDateTime end = current.plusMonths(1).atDay(1).atStartOfDay();
        // Publication, completion and actual recycling each use their own event timestamp.
        List<Map<String,Object>> events = db.queryForList("""
            SELECT month,SUM(published_items) published_items,SUM(completed_items) completed_items,
              SUM(recycle_count) recycle_count,SUM(recycle_weight) recycle_weight FROM (
              SELECT DATE_FORMAT(published_at,'%Y-%m') month,COUNT(*) published_items,
                0 completed_items,0 recycle_count,0 recycle_weight FROM items
                WHERE published_at>=? AND published_at<? GROUP BY month
              UNION ALL
              SELECT DATE_FORMAT(completed_at,'%Y-%m'),0,COUNT(*),0,0 FROM items
                WHERE status='COMPLETED' AND completed_at>=? AND completed_at<? GROUP BY DATE_FORMAT(completed_at,'%Y-%m')
              UNION ALL
              SELECT DATE_FORMAT(rr.recycled_at,'%Y-%m'),0,0,COUNT(*),SUM(rr.weight_kg)
                FROM recycling_records rr JOIN recycling_orders ro ON ro.id=rr.recycling_order_id
                WHERE ro.status='COMPLETED' AND rr.recycled_at>=? AND rr.recycled_at<?
                GROUP BY DATE_FORMAT(rr.recycled_at,'%Y-%m')
            ) events GROUP BY month ORDER BY month
            """,start,end,start,end,start,end);
        result.put("monthly", fillMonths(current, events));
        result.put("trend_start", current.minusMonths(11).toString());
        result.put("trend_end", current.toString());
        return result;
    }
    static List<Map<String,Object>> fillMonths(YearMonth current, List<Map<String,Object>> rows) {
        Map<String,Map<String,Object>> indexed = new HashMap<>();
        for (Map<String,Object> row : rows) indexed.put(String.valueOf(row.get("month")), row);
        List<Map<String,Object>> months = new ArrayList<>();
        for (int i=11;i>=0;i--) {
            String month = current.minusMonths(i).toString();
            months.add(indexed.getOrDefault(month, Map.of("month",month,"published_items",0,"completed_items",0,"recycle_count",0,"recycle_weight",0)));
        }
        return months;
    }
}
