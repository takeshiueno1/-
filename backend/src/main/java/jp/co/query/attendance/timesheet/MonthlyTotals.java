package jp.co.query.attendance.timesheet;

import java.util.List;

public record MonthlyTotals(
        int weekdayMinutes,
        int holidayMinutes,
        int halfDayCount,
        int requiredDays,
        int requiredMinutes,
        int differenceMinutes,
        List<SystemTotal> systemTotals) {

    public record SystemTotal(String systemCode, double days, int minutes) {}
}
