package edu.campus.recycle;

import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsTest {
    @Test void fillsTwelveMonthsAcrossYearBoundaryAndKeepsActualEvents() {
        Map<String,Object> event=Map.of("month","2026-01","published_items",0,"completed_items",1,"recycle_count",1,"recycle_weight",2.5);
        var months=StatisticsController.fillMonths(YearMonth.of(2026,11),List.of(event));
        assertEquals(12,months.size());assertEquals("2025-12",months.get(0).get("month"));
        assertEquals(event,months.get(1));assertEquals("2026-11",months.get(11).get("month"));
        assertEquals(0,months.get(11).get("recycle_weight"));
    }
}
