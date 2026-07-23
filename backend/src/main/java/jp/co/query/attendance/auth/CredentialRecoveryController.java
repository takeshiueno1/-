package jp.co.query.attendance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import jp.co.query.attendance.common.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api")
public class CredentialRecoveryController {

    public record RecoveryRequest(
            @NotBlank @Size(max = 50) String employeeCode,
            @NotBlank @Size(max = 100) String displayName) {}

    public record PendingRecovery(
            long id,
            String username,
            String employeeCode,
            String displayName,
            Instant requestedAt) {}

    private final JdbcClient jdbc;
    private final AuditLogRepository auditLogs;

    public CredentialRecoveryController(JdbcClient jdbc, AuditLogRepository auditLogs) {
        this.jdbc = jdbc;
        this.auditLogs = auditLogs;
    }

    @PostMapping("/auth/credential-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, String> request(@Valid @RequestBody RecoveryRequest request) {
        var employee = jdbc.sql("""
                        SELECT id, username
                          FROM employees
                         WHERE LOWER(employee_code) = LOWER(:employeeCode)
                           AND REPLACE(REPLACE(display_name, ' ', ''), '　', '')
                               = REPLACE(REPLACE(:displayName, ' ', ''), '　', '')
                        """)
                .param("employeeCode", request.employeeCode().strip())
                .param("displayName", request.displayName().strip())
                .query((rs, rowNum) -> new MatchedEmployee(
                        rs.getLong("id"), rs.getString("username")))
                .optional();
        employee.ifPresent(value -> {
            int inserted = jdbc.sql("""
                            INSERT INTO credential_recovery_requests (employee_id)
                            VALUES (:employeeId)
                            ON CONFLICT (employee_id) WHERE status = 'PENDING' DO NOTHING
                            """)
                    .param("employeeId", value.id())
                    .update();
            if (inserted == 1) {
                auditLogs.record(
                        "anonymous:credential-recovery",
                        "CREDENTIAL_RECOVERY_REQUESTED",
                        "USER",
                        value.username(),
                        "");
            }
        });
        return Map.of(
                "message",
                "入力内容が登録情報と一致する場合、管理者へ再発行依頼を送信しました。総務担当者へ本人確認を依頼してください。");
    }

    @GetMapping("/admin/credential-recovery")
    public List<PendingRecovery> pending() {
        return jdbc.sql("""
                        SELECT r.id, e.username, e.employee_code, e.display_name, r.requested_at
                          FROM credential_recovery_requests r
                          JOIN employees e ON e.id = r.employee_id
                         WHERE r.status = 'PENDING'
                         ORDER BY r.requested_at
                        """)
                .query((rs, rowNum) -> new PendingRecovery(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("employee_code"),
                        rs.getString("display_name"),
                        rs.getTimestamp("requested_at").toInstant()))
                .list();
    }

    private record MatchedEmployee(long id, String username) {}
}
