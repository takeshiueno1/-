package jp.co.query.attendance.employee;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.auth.AccessRole;
import jp.co.query.attendance.auth.PasswordPolicy;
import jp.co.query.attendance.integration.CredentialOutlookDraftService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminEmployeeService {

    public record EmployeeAccount(
            String username,
            boolean enabled,
            boolean mustChangePassword,
            String accessRole,
            String department,
            String displayName,
            String positionName,
            String employeeCode,
            String workScheduleType,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            String defaultSystemCode) {}

    public record CreateCommand(
            String username,
            String password,
            String accessRole,
            String department,
            String displayName,
            String positionName,
            String employeeCode,
            String workScheduleType,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            String defaultSystemCode) {}

    private final JdbcClient jdbc;
    private final JdbcUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogs;
    private final PasswordPolicy passwordPolicy;
    private final CredentialOutlookDraftService credentialDrafts;

    public AdminEmployeeService(
            JdbcClient jdbc,
            JdbcUserDetailsManager users,
            PasswordEncoder passwordEncoder,
            AuditLogRepository auditLogs,
            PasswordPolicy passwordPolicy,
            CredentialOutlookDraftService credentialDrafts) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditLogs = auditLogs;
        this.passwordPolicy = passwordPolicy;
        this.credentialDrafts = credentialDrafts;
    }

    @Transactional(readOnly = true)
    public List<EmployeeAccount> findAll() {
        return search("", 100);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAccount> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.sql("""
                        SELECT u.username, u.enabled, u.must_change_password,
                               CASE
                                 WHEN EXISTS (
                                   SELECT 1 FROM authorities a
                                    WHERE a.username = u.username AND a.authority = 'ROLE_ADMIN'
                                 ) THEN 'ADMIN'
                                 WHEN EXISTS (
                                   SELECT 1 FROM authorities a
                                    WHERE a.username = u.username AND a.authority = 'ROLE_MANAGER'
                                 ) THEN 'MANAGER'
                                 ELSE 'USER'
                               END AS access_role,
                               e.department, e.display_name, e.position_name,
                               e.employee_code, e.work_schedule_type, e.standard_start, e.standard_end,
                               e.standard_break_minutes, e.default_system_code
                          FROM users u
                          JOIN employees e ON e.username = u.username
                         WHERE :query = ''
                            OR POSITION(LOWER(:query) IN LOWER(
                                u.username || ' ' || e.display_name || ' ' ||
                                e.employee_code || ' ' || e.department)) > 0
                         ORDER BY e.employee_code, u.username
                         LIMIT :limit
                        """)
                .param("query", normalizedQuery)
                .param("limit", safeLimit)
                .query((rs, rowNum) -> new EmployeeAccount(
                        rs.getString("username"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("must_change_password"),
                        rs.getString("access_role"),
                        rs.getString("department"),
                        rs.getString("display_name"),
                        rs.getString("position_name"),
                        rs.getString("employee_code"),
                        rs.getString("work_schedule_type"),
                        rs.getTime("standard_start").toLocalTime(),
                        rs.getTime("standard_end").toLocalTime(),
                        rs.getInt("standard_break_minutes"),
                        rs.getString("default_system_code")))
                .list();
    }

    @Transactional
    public EmployeeAccount create(String actor, CreateCommand command) {
        String username = normalizeUsername(command.username());
        passwordPolicy.validate(command.password(), username);
        AccessRole accessRole = AccessRole.from(command.accessRole());
        if (!username.matches("[a-z0-9._-]{3,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザー名は半角英数字と . _ - を使い3～50文字で入力してください。");
        }
        if (blank(command.displayName()) || blank(command.employeeCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "氏名と社員コードは必須です。");
        }
        try {
            users.createUser(User.withUsername(username)
                    .password(passwordEncoder.encode(command.password()))
                    .roles(accessRole.springRoles().toArray(String[]::new))
                    .build());
            jdbc.sql("""
                            UPDATE users
                               SET must_change_password = TRUE,
                                   password_changed_at = CURRENT_TIMESTAMP
                             WHERE username = :username
                            """)
                    .param("username", username)
                    .update();
            jdbc.sql("""
                            INSERT INTO employees (
                                username, department, display_name, position_name, employee_code,
                                work_schedule_type, standard_start, standard_end,
                                standard_break_minutes, default_system_code)
                            VALUES (
                                :username, :department, :displayName, :positionName, :employeeCode,
                                :workScheduleType, :standardStart, :standardEnd,
                                :standardBreakMinutes, :defaultSystemCode)
                            """)
                    .param("username", username)
                    .param("department", normalize(command.department()))
                    .param("displayName", command.displayName().trim())
                    .param("positionName", normalize(command.positionName()))
                    .param("employeeCode", command.employeeCode().trim())
                    .param("workScheduleType", blank(command.workScheduleType()) ? "正社員（8時間）" : command.workScheduleType().trim())
                    .param("standardStart", Time.valueOf(command.standardStart()))
                    .param("standardEnd", Time.valueOf(command.standardEnd()))
                    .param("standardBreakMinutes", command.standardBreakMinutes())
                    .param("defaultSystemCode", normalize(command.defaultSystemCode()))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ユーザー名または社員コードが既に使用されています。", exception);
        }
        auditLogs.record(actor, "USER_CREATED", "USER", username, "");
        return findByUsername(username);
    }

    @Transactional
    public void changeEnabled(String actor, String targetUsername, boolean enabled) {
        String username = normalizeUsername(targetUsername);
        if (actor.equalsIgnoreCase(username) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身のアカウントは無効化できません。");
        }
        int updated = jdbc.sql("""
                        UPDATE users
                           SET enabled = :enabled,
                               failed_login_count = CASE WHEN :enabled THEN 0 ELSE failed_login_count END,
                               locked_until = CASE WHEN :enabled THEN NULL ELSE locked_until END
                         WHERE username = :username
                        """)
                .param("enabled", enabled)
                .param("username", username)
                .update();
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "利用者が見つかりません。" );
        auditLogs.record(actor, enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", username, "");
    }

    @Transactional
    public void resetPassword(String actor, String targetUsername, String newPassword) {
        String username = normalizeUsername(targetUsername);
        passwordPolicy.validate(newPassword, username);
        int updated = jdbc.sql("""
                        UPDATE users
                           SET password = :password,
                               failed_login_count = 0,
                               locked_until = NULL,
                               must_change_password = TRUE,
                               password_changed_at = CURRENT_TIMESTAMP
                         WHERE username = :username
                        """)
                .param("password", passwordEncoder.encode(newPassword))
                .param("username", username)
                .update();
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "利用者が見つかりません。" );
        jdbc.sql("""
                        UPDATE credential_recovery_requests r
                           SET status = 'RESOLVED',
                               resolved_at = CURRENT_TIMESTAMP,
                               resolved_by = :actor
                          FROM employees e
                         WHERE r.employee_id = e.id
                           AND e.username = :username
                           AND r.status = 'PENDING'
                        """)
                .param("actor", actor)
                .param("username", username)
                .update();
        auditLogs.record(actor, "PASSWORD_RESET", "USER", username, "");
    }

    @Transactional(readOnly = true)
    public CredentialOutlookDraftService.DraftToken createCredentialOutlookDraft(
            String actor,
            String targetUsername,
            String temporaryPassword) {
        String username = normalizeUsername(targetUsername);
        var account = jdbc.sql("""
                        SELECT u.password, u.enabled, e.display_name
                          FROM users u
                          JOIN employees e ON e.username = u.username
                         WHERE u.username = :username
                        """)
                .param("username", username)
                .query((rs, rowNum) -> new CredentialDraftAccount(
                        rs.getString("password"),
                        rs.getBoolean("enabled"),
                        rs.getString("display_name")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "利用者が見つかりません。"));
        if (!account.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "この利用者は無効です。有効化してから仮パスワードを送信してください。");
        }
        if (temporaryPassword == null
                || temporaryPassword.length() > 128
                || !passwordEncoder.matches(temporaryPassword, account.passwordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "表示中の仮パスワードは現在の認証情報と一致しません。再発行してください。");
        }
        return credentialDrafts.create(
                actor,
                username,
                account.displayName(),
                temporaryPassword);
    }

    private record CredentialDraftAccount(String passwordHash, boolean enabled, String displayName) {}

    private EmployeeAccount findByUsername(String username) {
        return findAll().stream().filter(account -> account.username().equals(username)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "利用者が見つかりません。"));
    }

    private static String normalizeUsername(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
