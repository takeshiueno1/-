package jp.co.query.attendance.employee;

import java.sql.Time;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeRepository {

    private final JdbcClient jdbc;

    public EmployeeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Employee> findByUsername(String username) {
        return jdbc.sql("""
                        SELECT id, username, department, display_name, position_name, employee_code,
                               work_schedule_type, standard_start, standard_end, standard_break_minutes,
                               default_system_code
                          FROM employees
                         WHERE username = :username
                        """)
                .param("username", username)
                .query((rs, rowNum) -> new Employee(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("department"),
                        rs.getString("display_name"),
                        rs.getString("position_name"),
                        rs.getString("employee_code"),
                        rs.getString("work_schedule_type"),
                        rs.getTime("standard_start").toLocalTime(),
                        rs.getTime("standard_end").toLocalTime(),
                        rs.getInt("standard_break_minutes"),
                        rs.getString("default_system_code")))
                .optional();
    }

    public void createProfileIfMissing(String username, String displayName, String employeeCode) {
        jdbc.sql("""
                        INSERT INTO employees (username, display_name, employee_code)
                        VALUES (:username, :displayName, :employeeCode)
                        ON CONFLICT (username) DO NOTHING
                        """)
                .param("username", username)
                .param("displayName", displayName)
                .param("employeeCode", employeeCode)
                .update();
    }
}
