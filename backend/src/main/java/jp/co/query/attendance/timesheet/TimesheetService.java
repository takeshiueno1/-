package jp.co.query.attendance.timesheet;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.employee.Employee;
import jp.co.query.attendance.employee.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TimesheetService {

    public record InitializeCommand(
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            String defaultSystemCode,
            boolean overwrite) {}

    public record UpdateEntryCommand(
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            String leaveType,
            String workDetail,
            String systemCode) {}

    public record HistoryItem(int year, int month) {}

    private final EmployeeRepository employees;
    private final TimesheetRepository timesheets;
    private final WorkTimeCalculator calculator;
    private final MonthlyAggregator aggregator;
    private final AuditLogRepository auditLogs;

    public TimesheetService(
            EmployeeRepository employees,
            TimesheetRepository timesheets,
            WorkTimeCalculator calculator,
            MonthlyAggregator aggregator,
            AuditLogRepository auditLogs) {
        this.employees = employees;
        this.timesheets = timesheets;
        this.calculator = calculator;
        this.aggregator = aggregator;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public Timesheet get(String username, int year, int month) {
        Employee employee = employee(username);
        validateMonth(year, month);
        TimesheetRepository.Header header = timesheets.findHeader(employee.id(), year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "勤務表がありません。対象月を初期化してください。"));
        return assemble(employee, header);
    }

    @Transactional
    public Timesheet getAsAdmin(String actor, String targetUsername, int year, int month) {
        Employee target = employee(targetUsername);
        validateMonth(year, month);
        TimesheetRepository.Header header = timesheets.findHeader(target.id(), year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "勤務表がありません。対象月を初期化してください。"));
        auditLogs.record(actor, "TIMESHEET_VIEWED_BY_ADMIN", "TIMESHEET", Long.toString(header.id()), "");
        return assemble(target, header);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(String username) {
        Employee employee = employee(username);
        return timesheets.findMonths(employee.id()).stream()
                .map(month -> new HistoryItem(month.year(), month.month()))
                .toList();
    }

    @Transactional
    public List<HistoryItem> historyAsAdmin(String actor, String targetUsername) {
        Employee target = employee(targetUsername);
        List<HistoryItem> result = timesheets.findMonths(target.id()).stream()
                .map(month -> new HistoryItem(month.year(), month.month()))
                .toList();
        auditLogs.record(actor, "TIMESHEET_HISTORY_VIEWED_BY_ADMIN", "EMPLOYEE", Long.toString(target.id()), "");
        return result;
    }

    @Transactional
    public Timesheet initialize(String username, int year, int month, InitializeCommand command) {
        return initializeFor(username, username, year, month, command);
    }

    @Transactional
    public Timesheet initializeAsAdmin(
            String actor, String targetUsername, int year, int month, InitializeCommand command) {
        return initializeFor(actor, targetUsername, year, month, command);
    }

    private Timesheet initializeFor(
            String actor, String targetUsername, int year, int month, InitializeCommand command) {
        Employee employee = employee(targetUsername);
        validateMonth(year, month);
        validateSettings(command);
        YearMonth target = YearMonth.of(year, month);
        int requiredWorkMinutes = requiredWorkMinutes(employee);
        LocalDate pmarkDate = nextBusinessDay(target.atEndOfMonth().plusDays(1));
        var existing = timesheets.findHeader(employee.id(), year, month);
        long timesheetId;
        if (existing.isPresent()) {
            if (!command.overwrite()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "勤務表は既に存在します。既存入力は変更していません。再初期化する場合は確認が必要です。");
            }
            timesheetId = existing.get().id();
            timesheets.resetHeader(
                    timesheetId,
                    command.standardStart(),
                    command.standardEnd(),
                    command.standardBreakMinutes(),
                    requiredWorkMinutes,
                    normalize(command.defaultSystemCode()),
                    pmarkDate);
        } else {
            timesheetId = timesheets.createHeader(
                    employee.id(),
                    year,
                    month,
                    command.standardStart(),
                    command.standardEnd(),
                    command.standardBreakMinutes(),
                    requiredWorkMinutes,
                    normalize(command.defaultSystemCode()),
                    pmarkDate);
        }

        Map<LocalDate, String> holidays = timesheets.holidaysBetween(target.atDay(1), target.atEndOfMonth());
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            DayType dayType = determineDayType(date, holidays);
            boolean workday = dayType == DayType.WORKDAY;
            String detail = holidays.getOrDefault(date,
                    dayType == DayType.SATURDAY ? "指定休日" : dayType == DayType.SUNDAY ? "普通休日" : "");
            LocalTime startTime = workday ? command.standardStart() : null;
            LocalTime endTime = workday ? command.standardEnd() : null;
            Integer breakMinutes = workday ? command.standardBreakMinutes() : null;
            String systemCode = workday ? normalize(command.defaultSystemCode()) : "";
            WorkCalculationResult calculated = calculator.calculate(
                    date,
                    dayType,
                    requiredWorkMinutes,
                    startTime,
                    endTime,
                    breakMinutes,
                    null,
                    detail,
                    systemCode,
                    command.defaultSystemCode());
            timesheets.insertEntry(
                    timesheetId,
                    date,
                    dayType,
                    calculated.startTime(),
                    calculated.endTime(),
                    calculated.breakMinutes(),
                    detail,
                    calculated.systemCode(),
                    calculated.weekdayMinutes(),
                    calculated.holidayMinutes());
        }
        String action = existing.isPresent() ? "TIMESHEET_REINITIALIZED" : "TIMESHEET_INITIALIZED";
        if (!actor.equalsIgnoreCase(targetUsername)) action += "_BY_ADMIN";
        auditLogs.record(actor, action,
                "TIMESHEET", Long.toString(timesheetId), year + "-" + month);
        TimesheetRepository.Header current = timesheets.findHeader(employee.id(), year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "勤務表がありません。"));
        return assemble(employee, current);
    }

    @Transactional
    public Timesheet updateEntry(String username, int year, int month, LocalDate date, UpdateEntryCommand command) {
        return updateEntryFor(username, username, year, month, date, command);
    }

    @Transactional
    public Timesheet updateEntryAsAdmin(
            String actor,
            String targetUsername,
            int year,
            int month,
            LocalDate date,
            UpdateEntryCommand command) {
        return updateEntryFor(actor, targetUsername, year, month, date, command);
    }

    private Timesheet updateEntryFor(
            String actor,
            String targetUsername,
            int year,
            int month,
            LocalDate date,
            UpdateEntryCommand command) {
        Employee employee = employee(targetUsername);
        TimesheetRepository.Header header = ownedHeader(employee, year, month);
        if (!YearMonth.from(date).equals(YearMonth.of(year, month))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "対象月以外の日付は更新できません。" );
        }
        validateRequiredHeaderFields(employee, header);
        TimesheetRepository.EntryRow entry = timesheets.findEntry(header.id(), date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "勤務日がありません。"));
        validateRequiredWorkdayFields(entry.dayType(), command);
        int standardMinutes = header.requiredWorkMinutes();
        WorkCalculationResult result = calculator.calculate(
                date,
                entry.dayType(),
                standardMinutes,
                command.startTime(),
                command.endTime(),
                command.breakMinutes(),
                command.leaveType(),
                command.workDetail(),
                command.systemCode(),
                header.defaultSystemCode());
        timesheets.updateEntry(
                entry.id(),
                result.startTime(),
                result.endTime(),
                result.breakMinutes(),
                command.leaveType(),
                command.workDetail(),
                result.systemCode(),
                result.weekdayMinutes(),
                result.holidayMinutes());
        String action = actor.equalsIgnoreCase(targetUsername)
                ? "DAILY_ENTRY_UPDATED"
                : "DAILY_ENTRY_UPDATED_BY_ADMIN";
        auditLogs.record(actor, action, "DAILY_ENTRY", Long.toString(entry.id()), date.toString());
        return assemble(employee, header);
    }

    @Transactional
    public Timesheet updateWgParticipation(String username, int year, int month, String value) {
        return updateWgParticipationFor(username, username, year, month, value);
    }

    @Transactional
    public Timesheet updateWgParticipationAsAdmin(
            String actor, String targetUsername, int year, int month, String value) {
        return updateWgParticipationFor(actor, targetUsername, year, month, value);
    }

    private Timesheet updateWgParticipationFor(
            String actor, String targetUsername, int year, int month, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WG参加可否は必須です。" );
        }
        if (!List.of("参加", "不参加", "当月未開催").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WG参加可否の値が不正です。" );
        }
        Employee employee = employee(targetUsername);
        TimesheetRepository.Header header = ownedHeader(employee, year, month);
        timesheets.updateWgParticipation(header.id(), value);
        String action = actor.equalsIgnoreCase(targetUsername)
                ? "WG_PARTICIPATION_UPDATED"
                : "WG_PARTICIPATION_UPDATED_BY_ADMIN";
        auditLogs.record(actor, action, "TIMESHEET", Long.toString(header.id()), "");
        return assemble(employee, header);
    }

    private Timesheet assemble(Employee employee, TimesheetRepository.Header header) {
        int standardMinutes = header.requiredWorkMinutes();
        List<DailyEntry> entries = timesheets.findEntries(header.id()).stream()
                .map(row -> {
                    WorkCalculationResult result = calculator.calculate(
                            row.workDate(),
                            row.dayType(),
                            standardMinutes,
                            row.startTime(),
                            row.endTime(),
                            row.breakMinutes(),
                            row.leaveType(),
                            row.workDetail(),
                            row.systemCode(),
                            header.defaultSystemCode());
                    return new DailyEntry(
                            row.id(), row.workDate(), row.dayType(), row.startTime(), row.endTime(),
                            row.breakMinutes(), row.leaveType(), row.workDetail(), row.systemCode(),
                            row.weekdayMinutes(), row.holidayMinutes(), result.warnings());
                })
                .toList();
        return new Timesheet(
                header.id(), header.year(), header.month(), header.standardStart(), header.standardEnd(),
                header.standardBreakMinutes(), header.defaultSystemCode(), header.wgParticipation(),
                header.pmarkConfirmationDate(), employee, entries, aggregator.aggregate(entries, standardMinutes));
    }

    private TimesheetRepository.Header ownedHeader(Employee employee, int year, int month) {
        validateMonth(year, month);
        return timesheets.findHeader(employee.id(), year, month)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "勤務表がありません。"));
    }

    private Employee employee(String username) {
        return employees.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "従業員情報がありません。"));
    }

    private LocalDate nextBusinessDay(LocalDate date) {
        LocalDate candidate = date;
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                || candidate.getDayOfWeek() == DayOfWeek.SUNDAY
                || timesheets.isHoliday(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static DayType determineDayType(LocalDate date, Map<LocalDate, String> holidays) {
        if (holidays.containsKey(date)) return DayType.HOLIDAY;
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) return DayType.SATURDAY;
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) return DayType.SUNDAY;
        return DayType.WORKDAY;
    }

    private static int standardWorkMinutes(LocalTime start, LocalTime end, int breakMinutes) {
        int startMinutes = start.getHour() * 60 + start.getMinute();
        int endMinutes = end.getHour() * 60 + end.getMinute();
        if (endMinutes <= startMinutes) endMinutes += 24 * 60;
        return endMinutes - startMinutes - breakMinutes;
    }

    private static int requiredWorkMinutes(Employee employee) {
        if (employee.workScheduleType().contains("6時間")) return 360;
        if (employee.workScheduleType().contains("7時間")) return 420;
        return 480;
    }

    private static void validateMonth(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "対象年月が不正です。" );
        }
    }

    private static void validateSettings(InitializeCommand command) {
        if (command.standardStart() == null || command.standardEnd() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "標準勤務時間を入力してください。" );
        }
        if (command.standardBreakMinutes() < 0 || command.standardBreakMinutes() > 1440) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休憩時間が不正です。" );
        }
        if (standardWorkMinutes(command.standardStart(), command.standardEnd(), command.standardBreakMinutes()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "標準勤務時間が不正です。" );
        }
    }

    private static void validateRequiredWorkdayFields(DayType dayType, UpdateEntryCommand command) {
        if (dayType != DayType.WORKDAY) return;
        var missing = new java.util.ArrayList<String>();
        if (command.startTime() == null) missing.add("始業");
        if (command.endTime() == null) missing.add("終業");
        if (command.breakMinutes() == null) missing.add("休憩");
        if (command.workDetail() == null || command.workDetail().isBlank()) missing.add("業務内容");
        if (command.systemCode() == null || command.systemCode().isBlank()) missing.add("システムNo.");
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.join("、", missing) + "は必須です。");
        }
    }

    private static void validateRequiredHeaderFields(Employee employee, TimesheetRepository.Header header) {
        var missing = new java.util.ArrayList<String>();
        if (employee.department() == null || employee.department().isBlank()) missing.add("所属");
        if (employee.displayName() == null || employee.displayName().isBlank()) missing.add("氏名");
        if (employee.positionName() == null || employee.positionName().isBlank()) missing.add("役職");
        if (employee.employeeCode() == null || employee.employeeCode().isBlank()) missing.add("コード");
        if (header.pmarkConfirmationDate() == null) missing.add("Pマーク");
        if (header.wgParticipation() == null || header.wgParticipation().isBlank()) missing.add("WG");
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.join("、", missing) + "は必須です。");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
