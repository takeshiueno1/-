package jp.co.query.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Optional;
import jp.co.query.attendance.employee.Employee;
import jp.co.query.attendance.employee.EmployeeRepository;
import jp.co.query.attendance.timesheet.TimesheetExportService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OutlookDraftServiceTest {

    @Test
    void createsOneTimeEmlWithWorkbookAttachment() {
        TimesheetExportService exports = mock(TimesheetExportService.class);
        when(exports.export("admin", 2026, 8))
                .thenReturn(new TimesheetExportService.ExportedWorkbook(
                        "勤務表_ADMIN_2026-08.xlsx",
                        "workbook-content".getBytes(StandardCharsets.UTF_8)));
        EmployeeRepository employees = mock(EmployeeRepository.class);
        when(employees.findByUsername("admin")).thenReturn(Optional.of(new Employee(
                1L,
                "admin",
                "総務部",
                "上野 豪",
                "管理者",
                "ADMIN",
                "REGULAR",
                LocalTime.of(9, 30),
                LocalTime.of(18, 30),
                60,
                "")));
        OutlookDraftService service = new OutlookDraftService(exports, employees);

        String token = service.create("admin", 2026, 8).token();
        OutlookDraftService.DraftMail mail = service.consume(token);
        String content = new String(mail.content(), StandardCharsets.UTF_8);
        String body = decodeTextBody(content);

        assertThat(mail.filename()).isEqualTo("Attendance_Outlook_2026-08.eml");
        assertThat(content).contains("X-Unsent: 1");
        assertThat(content).contains(encodedHeader("【勤務表提出】2026年08月分 上野 豪"));
        assertThat(content).contains("Content-Disposition: attachment");
        assertThat(content).contains(Base64Fixture.WORKBOOK);
        assertThat(body).isEqualTo("""
                ご担当者様

                お疲れ様です。上野 豪です。

                2026年08月分の勤務表を送付いたします。
                添付ファイルをご確認いただきますよう、お願いいたします。

                修正または確認事項がございましたら、ご連絡ください。
                以上、よろしくお願いいたします。
                """);
        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void keepsOnlyLatestPendingDraftForEachUser() {
        TimesheetExportService exports = mock(TimesheetExportService.class);
        when(exports.export("admin", 2026, 9))
                .thenReturn(new TimesheetExportService.ExportedWorkbook(
                        "勤務表_ADMIN_2026-09.xlsx",
                        "workbook-content".getBytes(StandardCharsets.UTF_8)));
        EmployeeRepository employees = mock(EmployeeRepository.class);
        when(employees.findByUsername("admin")).thenReturn(Optional.empty());
        OutlookDraftService service = new OutlookDraftService(exports, employees);

        String oldToken = service.create("admin", 2026, 8).token();
        String latestToken = service.create("admin", 2026, 9).token();

        assertThatThrownBy(() -> service.consume(oldToken))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThat(service.consume(latestToken).filename())
                .isEqualTo("Attendance_Outlook_2026-09.eml");
    }

    private static String encodedHeader(String value) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String decodeTextBody(String message) {
        String marker = "Content-Transfer-Encoding: base64\r\n\r\n";
        int start = message.indexOf(marker) + marker.length();
        int end = message.indexOf("\r\n--attendance-", start);
        return new String(
                Base64.getMimeDecoder().decode(message.substring(start, end)),
                StandardCharsets.UTF_8);
    }

    private static final class Base64Fixture {
        private static final String WORKBOOK = "d29ya2Jvb2stY29udGVudA==";
    }
}
