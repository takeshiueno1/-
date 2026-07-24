package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.employee.Employee;
import jp.co.query.attendance.employee.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock
    private EmployeeRepository employees;

    @Mock
    private TimesheetRepository timesheets;

    @Mock
    private WorkTimeCalculator calculator;

    @Mock
    private MonthlyAggregator aggregator;

    @Mock
    private AuditLogRepository auditLogs;

    @InjectMocks
    private TimesheetService service;

    @Test
    void returnsOwnedTimesheetMonthsInRepositoryOrder() {
        Employee employee = new Employee(
                10L,
                "test",
                "テスト部門",
                "テストユーザー",
                "",
                "TEST001",
                "正社員（8時間）",
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                "TEST001");
        when(employees.findByUsername("test")).thenReturn(Optional.of(employee));
        when(timesheets.findMonths(10L)).thenReturn(List.of(
                new TimesheetRepository.MonthHeader(2026, 6),
                new TimesheetRepository.MonthHeader(2026, 5)));

        assertThat(service.history("test")).containsExactly(
                new TimesheetService.HistoryItem(2026, 6),
                new TimesheetService.HistoryItem(2026, 5));
    }

    @Test
    void administratorCanListAnotherEmployeesMonthsAndTheReadIsAudited() {
        Employee target = new Employee(
                20L,
                "test",
                "テスト部門",
                "テストユーザー",
                "",
                "TEST001",
                "正社員（8時間）",
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                "TEST001");
        when(employees.findByUsername("test")).thenReturn(Optional.of(target));
        when(timesheets.findMonths(20L)).thenReturn(List.of(
                new TimesheetRepository.MonthHeader(2026, 7)));

        assertThat(service.historyAsAdmin("admin", "test")).containsExactly(
                new TimesheetService.HistoryItem(2026, 7));
        verify(auditLogs).record(
                "admin", "TIMESHEET_HISTORY_VIEWED_BY_ADMIN", "EMPLOYEE", "20", "");
    }

    @Test
    void rejectsMissingRequiredFieldsOnWorkdayBeforeCalculation() {
        Employee employee = new Employee(
                10L,
                "test",
                "テスト部門",
                "テストユーザー",
                "担当",
                "TEST001",
                "正社員（8時間）",
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                "TEST001");
        var header = new TimesheetRepository.Header(
                100L,
                10L,
                2026,
                7,
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                480,
                "TEST001",
                "参加",
                LocalDate.of(2026, 8, 3));
        LocalDate workDate = LocalDate.of(2026, 7, 1);
        var entry = new TimesheetRepository.EntryRow(
                1000L,
                workDate,
                DayType.WORKDAY,
                null,
                null,
                null,
                null,
                "",
                "",
                null,
                null);
        when(employees.findByUsername("test")).thenReturn(Optional.of(employee));
        when(timesheets.findHeader(10L, 2026, 7)).thenReturn(Optional.of(header));
        when(timesheets.findEntry(100L, workDate)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.updateEntry(
                "test",
                2026,
                7,
                workDate,
                new TimesheetService.UpdateEntryCommand(null, null, null, null, "", "")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("始業、終業、休憩、業務内容、システムNo.は必須です。");
                });
        verifyNoInteractions(calculator);
    }
}
