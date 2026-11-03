package edu.campus.recycle;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TradingMeetingTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private Map<String,Object> meeting(String date,String time,String location) {
        return Map.of("meetingDate",date,"meetingTime",time,"meetingLocation",location);
    }
    @Test void acceptsFutureDatesAndOpeningAndClosingTimes() {
        assertDoesNotThrow(()->TradingMeeting.validate("SELL",meeting("2026-11-03","08:00","南苑一站式"),clock));
        assertDoesNotThrow(()->TradingMeeting.validate("GIVE",meeting("2026-11-03","21:00","北苑食堂"),clock));
        assertDoesNotThrow(()->TradingMeeting.validate("SELL",meeting("2026-11-02","10:30","南苑食堂"),clock));
    }
    @ParameterizedTest @ValueSource(strings={"07:30","21:30","22:00","08:60","8:00","09:15","10:30:00"})
    void rejectsTimesOutsideAllowedSlots(String time) {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("2026-11-03",time,"南苑食堂"),clock));
    }
    @ParameterizedTest @ValueSource(strings={"周一","明天","2026-02-30","2026-13-02","2026-11-1","","2026-11-01"})
    void rejectsInvalidOrPastDates(String date) {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting(date,"12:00","南苑食堂"),clock));
    }
    @ParameterizedTest @ValueSource(strings={"09:30","10:00"})
    void rejectsElapsedTimesToday(String time) {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("2026-11-02",time,"南苑食堂"),clock));
    }
    @Test void rejectsUnknownPlaceAndLegacyWeekdayOnlyPayload() {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",meeting("2026-11-03","09:00","校外"),clock));
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",Map.of("meetingDay","周一","meetingTime","09:00","meetingLocation","南苑食堂"),clock));
    }
    @Test void sellingAndGivingRequireMeetingButRecyclingUsesAppointment() {
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("SELL",Map.of(),clock));
        assertThrows(ResponseStatusException.class,()->TradingMeeting.validate("GIVE",Map.of(),clock));
        assertDoesNotThrow(()->TradingMeeting.validate("RECYCLE",Map.of(),clock));
    }
    @Test void oldListingsWithoutSpecificDateCannotCreateNewOrders() {
        var item=new HashMap<String,Object>(Map.of("process_type","SELL","meeting_day","周一","meeting_time","10:00","meeting_location","南苑食堂"));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->TradingMeeting.validateStored(item)).getStatusCode());
    }
}
