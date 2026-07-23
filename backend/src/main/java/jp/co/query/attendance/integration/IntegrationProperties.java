package jp.co.query.attendance.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.integrations")
public record IntegrationProperties(
        String publicBaseUrl,
        Outlook outlook,
        Slack slack) {

    public record Outlook(
            boolean enabled,
            String tenantId,
            String clientId,
            String clientSecret,
            String sender) {}

    public record Slack(
            boolean enabled,
            String webhookUrl) {}
}
