package jp.co.query.attendance.auth;

import java.time.LocalTime;
import jp.co.query.attendance.employee.AdminEmployeeService;
import jp.co.query.attendance.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.test-user.enabled", havingValue = "true")
public class TestUserInitializer implements ApplicationRunner {

    private final AdminEmployeeService employeeService;
    private final EmployeeRepository employees;
    private final String password;

    public TestUserInitializer(
            AdminEmployeeService employeeService,
            EmployeeRepository employees,
            @Value("${app.test-user.password:}") String password) {
        this.employeeService = employeeService;
        this.employees = employees;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.findByUsername("test").isPresent()) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("APP_TEST_USER_ENABLED=true の場合は APP_TEST_USER_PASSWORD を設定してください。");
        }
        employeeService.create("system:test-user", new AdminEmployeeService.CreateCommand(
                "test",
                password,
                "ソリューション事業部",
                "上野 豪",
                "",
                "TEST001",
                "正社員（8時間）",
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                ""));
    }
}
