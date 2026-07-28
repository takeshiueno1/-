package jp.co.query.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jp.co.query.attendance.common.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CredentialOutlookDraftServiceTest {

    @Test
    void createsOneTimePrivateDraftWithoutUsername() {
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        CredentialOutlookDraftService service = new CredentialOutlookDraftService(auditLogs);

        String token = service.create("admin", "ueno", "上野 豪", "Temporary-2026-A").token();
        CredentialOutlookDraftService.DraftMail mail = service.consume(token);
        String content = new String(mail.content(), StandardCharsets.UTF_8);
        String encodedBody = content.substring(content.indexOf("\r\n\r\n") + 4).strip();
        String body = new String(Base64.getMimeDecoder().decode(encodedBody), StandardCharsets.UTF_8);

        assertThat(content).contains("X-Unsent: 1").contains("Sensitivity: Private");
        assertThat(body).contains("Temporary-2026-A").contains("Outlookの「暗号化」");
        assertThat(body).doesNotContain("ueno");
        verify(auditLogs).record(
                "admin",
                "TEMP_CREDENTIAL_OUTLOOK_DRAFT_CREATED",
                "USER",
                "ueno",
                "");
        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
