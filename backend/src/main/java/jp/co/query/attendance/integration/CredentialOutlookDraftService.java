package jp.co.query.attendance.integration;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.co.query.attendance.common.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CredentialOutlookDraftService {

    public record DraftToken(String token) {}

    public record DraftMail(String filename, byte[] content) {}

    private record PendingDraft(
            String actor,
            String targetUsername,
            String displayName,
            String temporaryPassword,
            Instant expiresAt) {}

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuditLogRepository auditLogs;
    private final Map<String, PendingDraft> pending = new ConcurrentHashMap<>();

    public CredentialOutlookDraftService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public DraftToken create(
            String actor,
            String targetUsername,
            String displayName,
            String temporaryPassword) {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry ->
                !entry.getValue().expiresAt().isAfter(now)
                        || entry.getValue().targetUsername().equals(targetUsername));
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        pending.put(token, new PendingDraft(
                actor,
                targetUsername,
                displayName,
                temporaryPassword,
                now.plus(TOKEN_LIFETIME)));
        auditLogs.record(actor, "TEMP_CREDENTIAL_OUTLOOK_DRAFT_CREATED", "USER", targetUsername, "");
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
        String subject = "【要暗号化】勤怠管理システム 仮パスワード";
        String body = """
                %s 様

                勤怠管理システムの仮パスワードをお知らせします。

                仮パスワード: %s

                ・ユーザーIDはこのメールに記載していません。別の連絡手段で確認してください。
                ・初回ログイン後、本人の新しいパスワードへの変更が必要です。
                ・このメールは本人確認済みの宛先に、Outlookの「暗号化」を有効にして送信してください。
                ・送信後は管理者側に残った仮パスワードを破棄してください。
                """.formatted(draft.displayName(), draft.temporaryPassword());
        byte[] eml = createEml(subject, body);
        return new DraftMail(
                "Temporary_Password_" + Instant.now().toEpochMilli() + ".eml",
                eml);
    }

    static byte[] createEml(String subject, String body) {
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        String message = """
                MIME-Version: 1.0\r
                X-Unsent: 1\r
                Sensitivity: Private\r
                Subject: %s\r
                Content-Type: text/plain; charset="UTF-8"\r
                Content-Transfer-Encoding: base64\r
                \r
                %s\r
                """.formatted(encodedSubject, encodedBody);
        return message.getBytes(StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "仮パスワードメールの有効期限が切れています。管理者画面からもう一度操作してください。");
    }
}
