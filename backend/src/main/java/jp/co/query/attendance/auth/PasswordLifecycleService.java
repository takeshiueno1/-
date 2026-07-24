package jp.co.query.attendance.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PasswordLifecycleService {

    public record Status(Instant expiresAt, int daysRemaining, boolean warning, boolean expired) {}

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");
    private static final Duration WARNING_PERIOD = Duration.ofDays(10);

    private final JdbcClient jdbc;
    private final Clock clock;

    public PasswordLifecycleService(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.clock = Clock.systemUTC();
    }

    public Status find(String username) {
        Instant changedAt = jdbc.sql("""
                        SELECT password_changed_at
                          FROM users
                         WHERE username = :username
                        """)
                .param("username", username)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(clock.instant());
        return evaluate(changedAt, clock.instant());
    }

    static Status evaluate(Instant changedAt, Instant now) {
        Instant expiresAt = changedAt.atZone(BUSINESS_ZONE).plusMonths(1).toInstant();
        Duration remaining = Duration.between(now, expiresAt);
        boolean expired = remaining.isZero() || remaining.isNegative();
        long seconds = Math.max(0, remaining.getSeconds());
        int daysRemaining = expired ? 0 : Math.toIntExact((seconds + 86_399) / 86_400);
        boolean warning = !expired && remaining.compareTo(WARNING_PERIOD) <= 0;
        return new Status(expiresAt, daysRemaining, warning, expired);
    }
}
