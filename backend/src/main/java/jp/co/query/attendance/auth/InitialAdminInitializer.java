package jp.co.query.attendance.auth;

import jp.co.query.attendance.employee.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final JdbcUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employees;
    private final String username;
    private final String password;

    public InitialAdminInitializer(
            JdbcUserDetailsManager userDetailsService,
            PasswordEncoder passwordEncoder,
            EmployeeRepository employees,
            @Value("${app.initial-admin.username}") String username,
            @Value("${app.initial-admin.password}") String password) {
        this.users = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.employees = employees;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.userExists(username)) {
            employees.createProfileIfMissing(username, "管理者", "ADMIN");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("初期管理者は作成されません。APP_INITIAL_ADMIN_PASSWORD を設定してください。");
            return;
        }
        users.createUser(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER", "ADMIN")
                .build());
        employees.createProfileIfMissing(username, "管理者", "ADMIN");
        log.info("初期管理者アカウントを作成しました。");
    }
}
