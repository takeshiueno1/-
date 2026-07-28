package jp.co.query.attendance.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true")
public class DemoAccountPolicyInitializer implements ApplicationRunner {

    private final JdbcClient jdbc;

    public DemoAccountPolicyInitializer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.sql("""
                        UPDATE users
                           SET must_change_password = FALSE
                         WHERE username LIKE 'demo%'
                        """)
                .update();
    }
}
