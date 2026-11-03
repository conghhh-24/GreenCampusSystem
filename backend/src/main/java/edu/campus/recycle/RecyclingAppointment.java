package edu.campus.recycle;

import java.time.*;
import java.time.format.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RecyclingAppointment {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private RecyclingAppointment() {}
    public static LocalDateTime validate(Object value) { return validate(value, Clock.system(ZONE)); }
    static LocalDateTime validate(Object value, Clock clock) {
        LocalDateTime time;
        try { time = LocalDateTime.parse(String.valueOf(value), FORMAT); }
        catch (DateTimeParseException e) { throw bad("预约时间格式须为 yyyy-MM-dd HH:mm:ss，且日期有效"); }
        if (!time.isAfter(LocalDateTime.now(clock.withZone(ZONE)))) throw bad("预约时间须晚于当前时间");
        LocalTime slot = time.toLocalTime();
        if (slot.isBefore(LocalTime.of(8,0)) || slot.isAfter(LocalTime.of(21,0))
            || time.getMinute() % 30 != 0 || time.getSecond() != 0)
            throw bad("预约时间须在08:00—21:00之间，且为整点或半点");
        return time;
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
