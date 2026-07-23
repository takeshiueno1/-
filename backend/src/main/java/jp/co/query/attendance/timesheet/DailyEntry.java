package jp.co.query.attendance.timesheet;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DailyEntry(
        long id,
        LocalDate workDate,
        DayType dayType,
        LocalTime startTime,
        LocalTime endTime,
        Integer breakMinutes,
        String leaveType,
        String workDetail,
        String systemCode,
        Integer weekdayMinutes,
        Integer holidayMinutes,
        List<String> warnings) {}
