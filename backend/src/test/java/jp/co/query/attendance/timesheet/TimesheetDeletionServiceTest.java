package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jp.co.query.attendance.common.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class TimesheetDeletionServiceTest {

    @Mock
    private TimesheetRepository timesheets;
    @Mock
    private UserDetailsService users;
    @Mock
    private AuditLogRepository auditLogs;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private TimesheetDeletionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TimesheetDeletionService(timesheets, users, passwordEncoder, auditLogs);
        when(users.loadUserByUsername("test")).thenReturn(User.withUsername("test")
                .password(passwordEncoder.encode("correct-password"))
                .roles("USER")
                .build());
    }

    @Test
    void requiresExactConfirmation() {
        assertThatThrownBy(() -> service.deleteOwn(
                "test", 2026, 6, "correct-password", "削除"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(timesheets, never()).softDelete(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requiresCurrentPassword() {
        assertThatThrownBy(() -> service.deleteOwn(
                "test", 2026, 6, "wrong-password", "削除する"))
                .isInstanceOf(ResponseStatusException.class);

        verify(timesheets, never()).softDelete(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void softDeletesOwnTimesheetAndAudits() {
        when(timesheets.softDelete("test", 2026, 6, "test")).thenReturn(1);

        service.deleteOwn("test", 2026, 6, "correct-password", "削除する");

        verify(timesheets).softDelete("test", 2026, 6, "test");
        verify(auditLogs).record(
                "test", "TIMESHEET_DELETED", "TIMESHEET",
                "test:2026-6", "softDelete=true");
    }
}
