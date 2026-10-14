package edu.campus.recycle;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TradingMeetingTest {
    private Map<String,Object> meeting(String day,String time,String location) {
        return Map.of("meetingDay",day,"meetingTime",time,"meetingLocation",location);
    }
    @Test void acceptsOpeningAndClosingTimes() {
        assertDoesNotThrow(()->TradingMeeting.validate("SELL",meeting("周一","08:00","南苑一站式")));
        assertDoesNotThrow(()->TradingMeeting.validate("GIVE",meeting("周日","21:00","北苑食堂")));
    }
    @Test void rejectsTimesOutsideAllowedHours() {
        for(String time:new String[]{"07:30","21:30","22:00","08:60","8:00"})
            assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("周一",time,"南苑食堂")));
    }
    @Test void rejectsUnknownDayAndPlace() {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("明天","09:00","北苑一站式")));
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("周六","09:00","校外")));
    }
    @Test void sellingAndGivingRequireMeetingButRecyclingUsesAppointment() {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",Map.of()));
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("GIVE",Map.of()));
        assertDoesNotThrow(()->TradingMeeting.validate("RECYCLE",Map.of()));
    }
}
