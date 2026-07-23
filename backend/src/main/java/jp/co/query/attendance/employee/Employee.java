package jp.co.query.attendance.employee;

import java.time.LocalTime;

public record Employee(
        long id,
        String username,
        String department,
        String displayName,
        String positionName,
        String employeeCode,
        String workScheduleType,
        LocalTime standardStart,
        LocalTime standardEnd,
        int standardBreakMinutes,
        String defaultSystemCode) {}
