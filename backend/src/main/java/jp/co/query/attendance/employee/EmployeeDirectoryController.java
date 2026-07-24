package jp.co.query.attendance.employee;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/employees")
public class EmployeeDirectoryController {

    public record DirectoryEmployee(
            long id,
            String username,
            String department,
            String displayName,
            String positionName,
            String employeeCode,
            String workScheduleType,
            String standardStart,
            String standardEnd,
            int standardBreakMinutes,
            String defaultSystemCode) {}

    private final JdbcClient jdbc;

    public EmployeeDirectoryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<DirectoryEmployee> search(
            @RequestParam(name = "query", defaultValue = "") @Size(max = 100) String query,
            @RequestParam(name = "limit", defaultValue = "100") @Min(1) @Max(100) int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.sql("""
                        SELECT e.id, e.username, e.department, e.display_name, e.position_name,
                               e.employee_code, e.work_schedule_type, e.standard_start, e.standard_end,
                               e.standard_break_minutes, e.default_system_code
                          FROM employees e
                          JOIN users u ON u.username = e.username
                         WHERE u.enabled = TRUE
                           AND (
                             :query = ''
                             OR POSITION(LOWER(:query) IN LOWER(
                               e.username || ' ' || e.display_name || ' ' ||
                               e.employee_code || ' ' || e.department)) > 0
                           )
                         ORDER BY e.employee_code, e.username
                         LIMIT :limit
                        """)
                .param("query", normalizedQuery)
                .param("limit", safeLimit)
                .query((rs, rowNum) -> new DirectoryEmployee(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("department"),
                        rs.getString("display_name"),
                        rs.getString("position_name"),
                        rs.getString("employee_code"),
                        rs.getString("work_schedule_type"),
                        rs.getTime("standard_start").toLocalTime().toString(),
                        rs.getTime("standard_end").toLocalTime().toString(),
                        rs.getInt("standard_break_minutes"),
                        rs.getString("default_system_code")))
                .list();
    }
}
