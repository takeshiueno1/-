package jp.co.query.attendance.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.test-user.enabled", havingValue = "true")
public class TestUserInitializer implements ApplicationRunner {

    private final JdbcUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;
    private final String generalPassword;
    private final String managerPassword;

    public TestUserInitializer(
            JdbcUserDetailsManager users,
            PasswordEncoder passwordEncoder,
            JdbcClient jdbc,
            @Value("${app.test-user.password:}") String generalPassword,
            @Value("${app.test-user.manager-password:}") String managerPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.generalPassword = generalPassword;
        this.managerPassword = managerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (generalPassword == null || generalPassword.isBlank()
                || managerPassword == null || managerPassword.isBlank()) {
            throw new IllegalStateException(
                    "APP_TEST_USER_ENABLED=true の場合は "
                            + "APP_TEST_USER_PASSWORD と APP_TEST_MANAGER_PASSWORD を設定してください。");
        }
        upsertUser(
                "test",
                generalPassword,
                AccessRole.USER,
                "ソリューション事業部",
                "上野 豪",
                "",
                "TEST001",
                "");
        upsertUser(
                "test02",
                managerPassword,
                AccessRole.MANAGER,
                "ソリューション事業部",
                "役職者テスト",
                "役職者",
                "TEST002",
                "");
    }

    private void upsertUser(
            String username,
            String rawPassword,
            AccessRole accessRole,
            String department,
            String displayName,
            String positionName,
            String employeeCode,
            String defaultSystemCode) {
        var user = User.withUsername(username)
                .password(passwordEncoder.encode(rawPassword))
                .roles(accessRole.springRoles().toArray(String[]::new))
                .build();
        if (users.userExists(username)) {
            users.updateUser(user);
        } else {
            users.createUser(user);
        }
        jdbc.sql("""
                        UPDATE users
                           SET enabled = TRUE,
                               failed_login_count = 0,
                               locked_until = NULL,
                               must_change_password = FALSE,
                               password_changed_at = CURRENT_TIMESTAMP
                         WHERE username = :username
                        """)
                .param("username", username)
                .update();
        jdbc.sql("""
                        INSERT INTO employees (
                            username, department, display_name, position_name, employee_code,
                            work_schedule_type, standard_start, standard_end,
                            standard_break_minutes, default_system_code)
                        VALUES (
                            :username, :department, :displayName, :positionName, :employeeCode,
                            '正社員（8時間）', TIME '09:30', TIME '18:30', 60, :defaultSystemCode)
                        ON CONFLICT (username) DO UPDATE
                           SET department = EXCLUDED.department,
                               display_name = EXCLUDED.display_name,
                               position_name = EXCLUDED.position_name,
                               employee_code = EXCLUDED.employee_code,
                               default_system_code = EXCLUDED.default_system_code,
                               updated_at = CURRENT_TIMESTAMP
                        """)
                .param("username", username)
                .param("department", department)
                .param("displayName", displayName)
                .param("positionName", positionName)
                .param("employeeCode", employeeCode)
                .param("defaultSystemCode", defaultSystemCode)
                .update();
    }
}
