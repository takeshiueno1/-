package jp.co.query.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.timesheet.TimesheetService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class CommunicationIntegrationServiceTest {

    @Test
    void reportsDisabledWhenCredentialsAreMissing() {
        var service = service(new IntegrationProperties(
                "",
                new IntegrationProperties.Outlook(false, "", "", "", ""),
                new IntegrationProperties.Slack(false, "")));

        assertThat(service.status().outlookConfigured()).isFalse();
        assertThat(service.status().slackConfigured()).isFalse();
    }

    @Test
    void reportsConfiguredOnlyForSupportedEndpoints() {
        var service = service(new IntegrationProperties(
                "https://attendance.example.test",
                new IntegrationProperties.Outlook(
                        true, "tenant.example", "client", "secret", "attendance@example.test"),
                new IntegrationProperties.Slack(
                        true, "https://hooks.slack.com/services/T000/B000/secret")));

        assertThat(service.status().outlookConfigured()).isTrue();
        assertThat(service.status().slackConfigured()).isTrue();

        var unsafeSlack = service(new IntegrationProperties(
                "",
                new IntegrationProperties.Outlook(false, "", "", "", ""),
                new IntegrationProperties.Slack(true, "https://example.test/services/secret")));
        assertThat(unsafeSlack.status().slackConfigured()).isFalse();
    }

    @Test
    void rejectsSendingBeforeReadingTimesheetWhenIntegrationIsDisabled() {
        var service = service(new IntegrationProperties(
                "",
                new IntegrationProperties.Outlook(false, "", "", "", ""),
                new IntegrationProperties.Slack(false, "")));

        assertThatThrownBy(() -> service.sendOutlook("test", "recipient@example.test", 2026, 6))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Outlook連携が設定されていません");
        assertThatThrownBy(() -> service.sendSlack("test", "", 2026, 6))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slack連携が設定されていません");
    }

    private static CommunicationIntegrationService service(IntegrationProperties properties) {
        return new CommunicationIntegrationService(
                properties,
                org.mockito.Mockito.mock(TimesheetService.class),
                org.mockito.Mockito.mock(AuditLogRepository.class),
                RestClient.builder());
    }
}
