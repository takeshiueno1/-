package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
