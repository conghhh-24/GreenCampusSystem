package edu.campus.recycle;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class TradingMeeting {
    static void validate(String type, Map<String,Object> input) {
        if ("RECYCLE".equals(type)) return;
        String day=Objects.toString(input.get("meetingDay"), "");
        String time=Objects.toString(input.get("meetingTime"), "");
        String location=Objects.toString(input.get("meetingLocation"), "");
        if (!Set.of("周一","周二","周三","周四","周五","周六","周日").contains(day))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择周一至周日的交易星期");
        if (!time.matches("(0[89]|1[0-9]|20):[0-5][0-9]|21:00"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"交易时间必须在08:00至21:00之间");
        if (!Set.of("南苑一站式","南苑食堂","北苑一站式","北苑食堂").contains(location))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效的交易地点");
    }
}
