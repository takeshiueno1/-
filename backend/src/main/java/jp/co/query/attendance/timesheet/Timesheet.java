package jp.co.query.attendance.timesheet;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import jp.co.query.attendance.employee.Employee;

public record Timesheet(
        long id,
        int year,
        int month,
        LocalTime standardStart,
        LocalTime standardEnd,
        int standardBreakMinutes,
        String defaultSystemCode,
        String wgParticipation,
        LocalDate pmarkConfirmationDate,
        Employee employee,
        List<DailyEntry> entries,
        MonthlyTotals totals) {}
