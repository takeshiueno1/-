package jp.co.query.attendance.demo;

import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import jp.co.query.attendance.employee.AdminEmployeeService;
import jp.co.query.attendance.employee.EmployeeRepository;
import jp.co.query.attendance.timesheet.TimesheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String ACTOR = "system:demo-seed";

    private final AdminEmployeeService employeeService;
    private final EmployeeRepository employees;
    private final TimesheetService timesheets;
    private final String password;
    private final int count;
    private final int months;

    public DemoDataSeeder(
            AdminEmployeeService employeeService,
            EmployeeRepository employees,
            TimesheetService timesheets,
            @Value("${app.demo.seed-password:}") String password,
            @Value("${app.demo.seed-count:50}") int count,
            @Value("${app.demo.seed-months:6}") int months) {
        this.employeeService = employeeService;
        this.employees = employees;
        this.timesheets = timesheets;
        this.password = password;
        this.count = Math.max(1, Math.min(count, 100));
        this.months = Math.max(1, Math.min(months, 24));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("APP_DEMO_SEED_PASSWORDは12～128文字で設定してください。");
        }
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Tokyo"));
        int createdUsers = 0;
        int createdSheets = 0;
        for (int number = 1; number <= count; number++) {
            String suffix = "%03d".formatted(number);
            String username = "demo" + suffix;
            String employeeCode = "DEMO" + suffix;
            if (employees.findByUsername(username).isEmpty()) {
                employeeService.create(ACTOR, new AdminEmployeeService.CreateCommand(
                        username,
                        password,
                        "検証部門" + (((number - 1) % 5) + 1),
                        "検証社員" + suffix,
                        number % 10 == 0 ? "リーダー" : "担当",
                        employeeCode,
                        "正社員（8時間）",
                        LocalTime.of(9, 30),
                        LocalTime.of(18, 30),
                        60,
                        "PJ" + (((number - 1) % 8) + 1)));
                createdUsers++;
            }
            for (int offset = 0; offset < months; offset++) {
                YearMonth target = current.minusMonths(offset);
                try {
                    timesheets.initializeAsAdmin(
                            ACTOR,
                            username,
                            target.getYear(),
                            target.getMonthValue(),
                            new TimesheetService.InitializeCommand(
                                    LocalTime.of(9, 30),
                                    LocalTime.of(18, 30),
                                    60,
                                    "PJ" + (((number - 1) % 8) + 1),
                                    false));
                    createdSheets++;
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 409) throw exception;
                }
            }
        }
        LOGGER.info("ローカル検証データを準備しました。利用者作成数={}, 勤務表作成数={}", createdUsers, createdSheets);
    }
}
