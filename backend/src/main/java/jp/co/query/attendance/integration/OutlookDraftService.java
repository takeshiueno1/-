package jp.co.query.attendance.integration;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.co.query.attendance.employee.EmployeeRepository;
import jp.co.query.attendance.timesheet.TimesheetExportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OutlookDraftService {

    public record DraftToken(String token) {}

    public record DraftMail(String filename, byte[] content) {}

    private record PendingDraft(String username, int year, int month, Instant expiresAt) {}

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TimesheetExportService exports;
    private final EmployeeRepository employees;
    private final Map<String, PendingDraft> pending = new ConcurrentHashMap<>();

    public OutlookDraftService(TimesheetExportService exports, EmployeeRepository employees) {
        this.exports = exports;
        this.employees = employees;
    }

    public DraftToken create(String username, int year, int month) {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry ->
                !entry.getValue().expiresAt().isAfter(now)
                        || entry.getValue().username().equals(username));
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        pending.put(token, new PendingDraft(username, year, month, now.plus(TOKEN_LIFETIME)));
        return new DraftToken(token);
    }

    public DraftMail consume(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw unavailable();
        }
        PendingDraft draft = pending.remove(token);
        if (draft == null || !draft.expiresAt().isAfter(Instant.now())) {
            throw unavailable();
        }
        TimesheetExportService.ExportedWorkbook workbook =
                exports.export(draft.username(), draft.year(), draft.month());
        String displayName = employees.findByUsername(draft.username())
                .map(employee -> employee.displayName())
                .filter(name -> !name.isBlank())
                .map(String::strip)
                .orElse(draft.username());
        String targetMonth = "%d年%02d月分".formatted(draft.year(), draft.month());
        String subject = "【勤務表提出】%s %s".formatted(targetMonth, displayName);
        String body = """
                ご担当者様

                お疲れ様です。%sです。

                %sの勤務表を送付いたします。
                添付ファイルをご確認いただきますよう、お願いいたします。

                修正または確認事項がございましたら、ご連絡ください。
                以上、よろしくお願いいたします。
                """.formatted(displayName, targetMonth);
        byte[] eml = createEml(subject, body, workbook.filename(), workbook.content());
        String filename = "Attendance_Outlook_" + draft.year() + "-"
                + String.format("%02d", draft.month()) + ".eml";
        return new DraftMail(filename, eml);
    }

    static byte[] createEml(String subject, String body, String attachmentFilename, byte[] attachment) {
        String boundary = "attendance-" + java.util.UUID.randomUUID().toString().replace("-", "");
        String encodedFilename = java.net.URLEncoder.encode(attachmentFilename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        String encodedAttachment = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(attachment);
        String message = """
                MIME-Version: 1.0\r
                X-Unsent: 1\r
                Subject: %s\r
                Content-Type: multipart/mixed; boundary="%s"\r
                \r
                --%s\r
                Content-Type: text/plain; charset="UTF-8"\r
                Content-Transfer-Encoding: base64\r
                \r
                %s\r
                --%s\r
                Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; name*=UTF-8''%s\r
                Content-Transfer-Encoding: base64\r
                Content-Disposition: attachment; filename*=UTF-8''%s\r
                \r
                %s\r
                --%s--\r
                """.formatted(
                encodedSubject,
                boundary,
                boundary,
                encodedBody,
                boundary,
                encodedFilename,
                encodedFilename,
                encodedAttachment,
                boundary);
        return message.getBytes(StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Outlookメールの有効期限が切れています。勤務表画面からもう一度操作してください。");
    }
}
