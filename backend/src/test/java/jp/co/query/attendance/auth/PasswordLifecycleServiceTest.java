package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PasswordLifecycleServiceTest {

    @Test
    void startsWarningTenDaysBeforeExpiry() {
        Instant changedAt = Instant.parse("2026-06-24T03:00:00Z");
        var status = PasswordLifecycleService.evaluate(
                changedAt,
                Instant.parse("2026-07-14T03:00:00Z"));

        assertThat(status.expiresAt()).isEqualTo(Instant.parse("2026-07-24T03:00:00Z"));
        assertThat(status.daysRemaining()).isEqualTo(10);
        assertThat(status.warning()).isTrue();
        assertThat(status.expired()).isFalse();
    }

    @Test
    void doesNotWarnBeforeTenDayWindow() {
        var status = PasswordLifecycleService.evaluate(
                Instant.parse("2026-06-24T03:00:00Z"),
                Instant.parse("2026-07-13T03:00:00Z"));

        assertThat(status.daysRemaining()).isEqualTo(11);
        assertThat(status.warning()).isFalse();
        assertThat(status.expired()).isFalse();
    }

    @Test
    void marksPasswordExpiredAtDeadline() {
        var status = PasswordLifecycleService.evaluate(
                Instant.parse("2026-06-24T03:00:00Z"),
                Instant.parse("2026-07-24T03:00:00Z"));

        assertThat(status.daysRemaining()).isZero();
        assertThat(status.warning()).isFalse();
        assertThat(status.expired()).isTrue();
    }
}
