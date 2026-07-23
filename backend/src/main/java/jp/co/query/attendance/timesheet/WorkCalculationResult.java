package jp.co.query.attendance.timesheet;

import java.time.LocalTime;
import java.util.List;

public record WorkCalculationResult(
        LocalTime startTime,
        LocalTime endTime,
        Integer breakMinutes,
        String systemCode,
        Integer weekdayMinutes,
        Integer holidayMinutes,
        List<String> warnings) {}
