package jp.co.query.attendance.timesheet;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.co.query.attendance.common.GeneratedKeyJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TimesheetRepository {

    public record Header(
            long id,
            long employeeId,
            int year,
            int month,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            int requiredWorkMinutes,
            String defaultSystemCode,
            String wgParticipation,
            LocalDate pmarkConfirmationDate) {}

    public record EntryRow(
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
            Integer holidayMinutes) {}

    public record MonthHeader(int year, int month) {}

    private final JdbcClient jdbc;
    private final GeneratedKeyJdbc generatedKeys;

    public TimesheetRepository(JdbcClient jdbc, GeneratedKeyJdbc generatedKeys) {
        this.jdbc = jdbc;
        this.generatedKeys = generatedKeys;
    }

    public Optional<Header> findHeader(long employeeId, int year, int month) {
        return jdbc.sql("""
                        SELECT id, employee_id, target_year, target_month, standard_start, standard_end,
                               standard_break_minutes, required_work_minutes, default_system_code, wg_participation,
                               pmark_confirmation_date
                          FROM timesheets
                         WHERE employee_id = :employeeId
                           AND target_year = :year
                           AND target_month = :month
                           AND deleted_at IS NULL
                        """)
                .param("employeeId", employeeId)
                .param("year", year)
                .param("month", month)
                .query((rs, rowNum) -> new Header(
                        rs.getLong("id"),
                        rs.getLong("employee_id"),
                        rs.getInt("target_year"),
                        rs.getInt("target_month"),
                        rs.getTime("standard_start").toLocalTime(),
                        rs.getTime("standard_end").toLocalTime(),
                        rs.getInt("standard_break_minutes"),
                        rs.getInt("required_work_minutes"),
                        rs.getString("default_system_code"),
                        rs.getString("wg_participation"),
                        rs.getDate("pmark_confirmation_date") == null ? null : rs.getDate("pmark_confirmation_date").toLocalDate()))
                .optional();
    }

    public List<MonthHeader> findMonths(long employeeId) {
        return jdbc.sql("""
                        SELECT target_year, target_month
                         FROM timesheets
                         WHERE employee_id = :employeeId
                           AND deleted_at IS NULL
                         ORDER BY target_year DESC, target_month DESC
                        """)
                .param("employeeId", employeeId)
                .query((rs, rowNum) -> new MonthHeader(
                        rs.getInt("target_year"),
                        rs.getInt("target_month")))
                .list();
    }

    public long createHeader(
            long employeeId,
            int year,
            int month,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            int requiredWorkMinutes,
            String defaultSystemCode,
            LocalDate pmarkDate) {
        return generatedKeys.insert("""
                        INSERT INTO timesheets (
                            employee_id, target_year, target_month, standard_start, standard_end,
                            standard_break_minutes, required_work_minutes, default_system_code, pmark_confirmation_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                List.of(
                        employeeId,
                        year,
                        month,
                        Time.valueOf(standardStart),
                        Time.valueOf(standardEnd),
                        standardBreakMinutes,
                        requiredWorkMinutes,
                        defaultSystemCode,
                        Date.valueOf(pmarkDate)));
    }

    public void resetHeader(
            long id,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            int requiredWorkMinutes,
            String defaultSystemCode,
            LocalDate pmarkDate) {
        jdbc.sql("""
                        UPDATE timesheets
                           SET standard_start = :standardStart,
                               standard_end = :standardEnd,
                               standard_break_minutes = :standardBreakMinutes,
                               required_work_minutes = :requiredWorkMinutes,
                               default_system_code = :defaultSystemCode,
                               wg_participation = NULL,
                               pmark_confirmation_date = :pmarkDate,
                               updated_at = CURRENT_TIMESTAMP,
                               version = version + 1
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("standardStart", Time.valueOf(standardStart))
                .param("standardEnd", Time.valueOf(standardEnd))
                .param("standardBreakMinutes", standardBreakMinutes)
                .param("requiredWorkMinutes", requiredWorkMinutes)
                .param("defaultSystemCode", defaultSystemCode)
                .param("pmarkDate", Date.valueOf(pmarkDate))
                .update();
        jdbc.sql("DELETE FROM daily_entries WHERE timesheet_id = :id")
                .param("id", id)
                .update();
    }

    public void insertEntry(
            long timesheetId,
            LocalDate workDate,
            DayType dayType,
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            String workDetail,
            String systemCode,
            Integer weekdayMinutes,
            Integer holidayMinutes) {
        jdbc.sql("""
                        INSERT INTO daily_entries (
                            timesheet_id, work_date, day_type, start_time, end_time, break_minutes,
                            work_detail, system_code, weekday_minutes, holiday_minutes)
                        VALUES (
                            :timesheetId, :workDate, :dayType, :startTime, :endTime, :breakMinutes,
                            :workDetail, :systemCode, :weekdayMinutes, :holidayMinutes)
                        """)
                .param("timesheetId", timesheetId)
                .param("workDate", Date.valueOf(workDate))
                .param("dayType", dayType.name())
                .param("startTime", startTime == null ? null : Time.valueOf(startTime))
                .param("endTime", endTime == null ? null : Time.valueOf(endTime))
                .param("breakMinutes", breakMinutes)
                .param("workDetail", workDetail)
                .param("systemCode", systemCode)
                .param("weekdayMinutes", weekdayMinutes)
                .param("holidayMinutes", holidayMinutes)
                .update();
    }

    public List<EntryRow> findEntries(long timesheetId) {
        return jdbc.sql("""
                        SELECT id, work_date, day_type, start_time, end_time, break_minutes,
                               leave_type, work_detail, system_code, weekday_minutes, holiday_minutes
                          FROM daily_entries
                         WHERE timesheet_id = :timesheetId
                         ORDER BY work_date
                        """)
                .param("timesheetId", timesheetId)
                .query((rs, rowNum) -> new EntryRow(
                        rs.getLong("id"),
                        rs.getDate("work_date").toLocalDate(),
                        DayType.valueOf(rs.getString("day_type")),
                        rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time") == null ? null : rs.getTime("end_time").toLocalTime(),
                        (Integer) rs.getObject("break_minutes"),
                        rs.getString("leave_type"),
                        rs.getString("work_detail"),
                        rs.getString("system_code"),
                        (Integer) rs.getObject("weekday_minutes"),
                        (Integer) rs.getObject("holiday_minutes")))
                .list();
    }

    public Optional<EntryRow> findEntry(long timesheetId, LocalDate workDate) {
        return jdbc.sql("""
                        SELECT id, work_date, day_type, start_time, end_time, break_minutes,
                               leave_type, work_detail, system_code, weekday_minutes, holiday_minutes
                          FROM daily_entries
                         WHERE timesheet_id = :timesheetId AND work_date = :workDate
                        """)
                .param("timesheetId", timesheetId)
                .param("workDate", Date.valueOf(workDate))
                .query((rs, rowNum) -> new EntryRow(
                        rs.getLong("id"),
                        rs.getDate("work_date").toLocalDate(),
                        DayType.valueOf(rs.getString("day_type")),
                        rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time") == null ? null : rs.getTime("end_time").toLocalTime(),
                        (Integer) rs.getObject("break_minutes"),
                        rs.getString("leave_type"),
                        rs.getString("work_detail"),
                        rs.getString("system_code"),
                        (Integer) rs.getObject("weekday_minutes"),
                        (Integer) rs.getObject("holiday_minutes")))
                .optional();
    }

    public void updateEntry(
            long entryId,
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            String leaveType,
            String workDetail,
            String systemCode,
            Integer weekdayMinutes,
            Integer holidayMinutes) {
        jdbc.sql("""
                        UPDATE daily_entries
                           SET start_time = :startTime,
                               end_time = :endTime,
                               break_minutes = :breakMinutes,
                               leave_type = :leaveType,
                               work_detail = :workDetail,
                               system_code = :systemCode,
                               weekday_minutes = :weekdayMinutes,
                               holiday_minutes = :holidayMinutes,
                               updated_at = CURRENT_TIMESTAMP,
                               version = version + 1
                         WHERE id = :entryId
                        """)
                .param("entryId", entryId)
                .param("startTime", startTime == null ? null : Time.valueOf(startTime))
                .param("endTime", endTime == null ? null : Time.valueOf(endTime))
                .param("breakMinutes", breakMinutes)
                .param("leaveType", blankToNull(leaveType))
                .param("workDetail", normalize(workDetail))
                .param("systemCode", normalize(systemCode))
                .param("weekdayMinutes", weekdayMinutes)
                .param("holidayMinutes", holidayMinutes)
                .update();
    }

    public void updateWgParticipation(long timesheetId, String value) {
        jdbc.sql("""
                        UPDATE timesheets
                           SET wg_participation = :value,
                               updated_at = CURRENT_TIMESTAMP,
                               version = version + 1
                         WHERE id = :timesheetId
                        """)
                .param("timesheetId", timesheetId)
                .param("value", blankToNull(value))
                .update();
    }

    public void setSourceImport(long timesheetId, long importId) {
        jdbc.sql("""
                        UPDATE timesheets
                           SET source_import_id = :importId,
                               updated_at = CURRENT_TIMESTAMP,
                               version = version + 1
                         WHERE id = :timesheetId
                        """)
                .param("timesheetId", timesheetId)
                .param("importId", importId)
                .update();
    }

    public int softDelete(
            String targetUsername,
            int year,
            int month,
            String actor) {
        return jdbc.sql("""
                        UPDATE timesheets t
                           SET deleted_at = CURRENT_TIMESTAMP,
                               deleted_by = :actor,
                               updated_at = CURRENT_TIMESTAMP,
                               version = version + 1
                          FROM employees e
                         WHERE t.employee_id = e.id
                           AND e.username = :targetUsername
                           AND t.target_year = :year
                           AND t.target_month = :month
                           AND t.deleted_at IS NULL
                        """)
                .param("actor", actor)
                .param("targetUsername", targetUsername)
                .param("year", year)
                .param("month", month)
                .update();
    }

    public Map<LocalDate, String> holidaysBetween(LocalDate start, LocalDate end) {
        return jdbc.sql("""
                        SELECT work_date, holiday_name
                          FROM company_holidays
                         WHERE work_date BETWEEN :start AND :end
                        """)
                .param("start", Date.valueOf(start))
                .param("end", Date.valueOf(end))
                .query((rs, rowNum) -> Map.entry(rs.getDate("work_date").toLocalDate(), rs.getString("holiday_name")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean isHoliday(LocalDate date) {
        return jdbc.sql("SELECT COUNT(*) FROM company_holidays WHERE work_date = :date")
                .param("date", Date.valueOf(date))
                .query(Integer.class)
                .single() > 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
