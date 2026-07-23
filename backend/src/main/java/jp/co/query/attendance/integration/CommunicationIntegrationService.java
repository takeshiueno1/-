package jp.co.query.attendance.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import java.util.Map;
import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.timesheet.Timesheet;
import jp.co.query.attendance.timesheet.TimesheetService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CommunicationIntegrationService {

    public record Status(boolean outlookConfigured, boolean slackConfigured) {}

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    private final IntegrationProperties properties;
    private final TimesheetService timesheets;
    private final AuditLogRepository auditLogs;
    private final RestClient restClient;

    public CommunicationIntegrationService(
            IntegrationProperties properties,
            TimesheetService timesheets,
            AuditLogRepository auditLogs,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.timesheets = timesheets;
        this.auditLogs = auditLogs;
        this.restClient = restClientBuilder.build();
    }

    public Status status() {
        return new Status(outlookConfigured(), slackConfigured());
    }

    public void sendOutlook(String username, String recipient, int year, int month) {
        if (!outlookConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Outlook連携が設定されていません。管理者へ連絡してください。");
        }
        Timesheet timesheet = timesheets.get(username, year, month);
        IntegrationProperties.Outlook outlook = properties.outlook();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", outlook.clientId());
        form.add("client_secret", outlook.clientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");

        try {
            TokenResponse token = restClient.post()
                    .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", outlook.tenantId())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || blank(token.accessToken())) {
                throw new RestClientException("Microsoft Graph token was empty");
            }

            restClient.post()
                    .uri("https://graph.microsoft.com/v1.0/users/{sender}/sendMail", outlook.sender())
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outlookPayload(timesheet, recipient))
                    .retrieve()
                    .toBodilessEntity();
            auditLogs.record(username, "TIMESHEET_SHARED_OUTLOOK", "TIMESHEET",
                    Long.toString(timesheet.id()), year + "-" + month);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Outlookへ送信できませんでした。設定または通信状況を確認してください。");
        }
    }

    public void sendSlack(String username, String message, int year, int month) {
        if (!slackConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Slack連携が設定されていません。管理者へ連絡してください。");
        }
        Timesheet timesheet = timesheets.get(username, year, month);
        String note = message == null ? "" : message.trim();
        String text = "【勤務表の連絡】%sさん %d年%02d月%s%s"
                .formatted(
                        timesheet.employee().displayName(),
                        year,
                        month,
                        note.isBlank() ? "" : "\n" + note,
                        applicationLink());
        try {
            restClient.post()
                    .uri(properties.slack().webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
            auditLogs.record(username, "TIMESHEET_SHARED_SLACK", "TIMESHEET",
                    Long.toString(timesheet.id()), year + "-" + month);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Slackへ送信できませんでした。設定または通信状況を確認してください。");
        }
    }

    private Map<String, Object> outlookPayload(Timesheet timesheet, String recipient) {
        String subject = "【勤務表】%s %d年%02d月分"
                .formatted(timesheet.employee().displayName(), timesheet.year(), timesheet.month());
        String body = """
                %sさんの%d年%02d月分の勤務表です。

                平日労働時間: %s
                休日労働時間: %s
                過不足時間: %s%s

                このメールは勤怠管理システムから送信されました。
                """.formatted(
                timesheet.employee().displayName(),
                timesheet.year(),
                timesheet.month(),
                formatMinutes(timesheet.totals().weekdayMinutes()),
                formatMinutes(timesheet.totals().holidayMinutes()),
                formatSignedMinutes(timesheet.totals().differenceMinutes()),
                applicationLink());
        return Map.of(
                "message", Map.of(
                        "subject", subject,
                        "body", Map.of("contentType", "Text", "content", body),
                        "toRecipients", List.of(Map.of(
                                "emailAddress", Map.of("address", recipient)))),
                "saveToSentItems", true);
    }

    private String applicationLink() {
        String baseUrl = properties.publicBaseUrl();
        return blank(baseUrl) ? "" : "\n確認: " + baseUrl.replaceAll("/+$", "");
    }

    private boolean outlookConfigured() {
        IntegrationProperties.Outlook outlook = properties.outlook();
        return outlook != null
                && outlook.enabled()
                && !blank(outlook.tenantId())
                && outlook.tenantId().matches("[A-Za-z0-9.-]{1,100}")
                && !blank(outlook.clientId())
                && !blank(outlook.clientSecret())
                && validEmail(outlook.sender());
    }

    private boolean slackConfigured() {
        IntegrationProperties.Slack slack = properties.slack();
        if (slack == null || !slack.enabled() || blank(slack.webhookUrl())) return false;
        try {
            URI uri = URI.create(slack.webhookUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ("hooks.slack.com".equalsIgnoreCase(uri.getHost())
                    || "hooks.slack-gov.com".equalsIgnoreCase(uri.getHost()))
                    && uri.getPath().startsWith("/services/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String formatMinutes(int minutes) {
        return "%d:%02d".formatted(minutes / 60, Math.abs(minutes % 60));
    }

    private static String formatSignedMinutes(int minutes) {
        return (minutes < 0 ? "-" : "") + formatMinutes(Math.abs(minutes));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validEmail(String value) {
        return !blank(value)
                && value.length() <= 254
                && value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
