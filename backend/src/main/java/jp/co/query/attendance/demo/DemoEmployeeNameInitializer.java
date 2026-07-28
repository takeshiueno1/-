package jp.co.query.attendance.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoEmployeeNameInitializer implements ApplicationRunner {

    private final JdbcClient jdbc;

    public DemoEmployeeNameInitializer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int number = 1; number <= DemoEmployeeNames.size(); number++) {
            jdbc.sql("""
                            UPDATE employees
                               SET display_name = :displayName,
                                   updated_at = CURRENT_TIMESTAMP
                             WHERE username = :username
                            """)
                    .param("displayName", DemoEmployeeNames.get(number))
                    .param("username", "demo%03d".formatted(number))
                    .update();
        }
    }
}
