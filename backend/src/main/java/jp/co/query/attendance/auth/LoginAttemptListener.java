package jp.co.query.attendance.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptListener {

    private final JdbcClient jdbc;

    public LoginAttemptListener(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = normalize(event.getAuthentication().getName());
        if (username.isBlank()) return;
        jdbc.sql("""
                        UPDATE users
                           SET failed_login_count = failed_login_count + 1,
                               locked_until = CASE
                                   WHEN failed_login_count + 1 >= 5 THEN :lockedUntil
                                   ELSE locked_until
                               END
                         WHERE username = :username
                        """)
                .param("username", username)
                .param("lockedUntil", Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .update();
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = normalize(event.getAuthentication().getName());
        jdbc.sql("""
                        UPDATE users
                           SET failed_login_count = 0,
                               locked_until = NULL
                         WHERE username = :username
                        """)
                .param("username", username)
                .update();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
