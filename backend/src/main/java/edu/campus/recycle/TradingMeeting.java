package edu.campus.recycle;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.*;
import java.time.format.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class TradingMeeting {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    static void validate(String type, Map<String,Object> input) {
        validate(type, input, Clock.system(ZONE));
    }
    static void validate(String type, Map<String,Object> input, Clock clock) {
        if ("RECYCLE".equals(type)) return;
        String date=Objects.toString(input.get("meetingDate"), "");
        String time=Objects.toString(input.get("meetingTime"), "");
        String location=Objects.toString(input.get("meetingLocation"), "");
        LocalDate day;
        try { day=LocalDate.parse(date, DATE); }
        catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效的交易日期，格式为yyyy-MM-dd");
        }
        if (!time.matches("(0[89]|1[0-9]|20):(00|30)|21:00"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"交易时间须在08:00至21:00之间，且为整点或半点");
        if (!LocalDateTime.of(day, LocalTime.parse(time)).isAfter(LocalDateTime.now(clock.withZone(ZONE))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"交易日期和时间须晚于当前时间");
        if (!Set.of("南苑一站式","南苑食堂","北苑一站式","北苑食堂").contains(location))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效的交易地点");
    }
    static void validateStored(Map<String,Object> item) {
        Map<String,Object> meeting = new java.util.HashMap<>();
        meeting.put("meetingDate", item.get("meeting_date"));
        meeting.put("meetingTime", item.get("meeting_time"));
        meeting.put("meetingLocation", item.get("meeting_location"));
        try { validate(String.valueOf(item.get("process_type")), meeting); }
        catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"物品交易日期已过期或尚未补全，请发布者编辑日期并重新审核");
        }
    }
}
