package jp.co.query.attendance.auth;

import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class LoginAccountStatusService {

    private final JdbcClient jdbc;

    public LoginAccountStatusService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isDisabled(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._-]{3,50}")) {
            return false;
        }
        return jdbc.sql("SELECT enabled FROM users WHERE username = :username")
                .param("username", username)
                .query(Boolean.class)
                .optional()
                .map(enabled -> !enabled)
                .orElse(false);
    }
}
