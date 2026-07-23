package jp.co.query.attendance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import jp.co.query.attendance.common.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    private final UserDetailsService users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;
    private final AuditLogRepository auditLogs;
    private final PasswordPolicy passwordPolicy;

    public PasswordController(
            UserDetailsService users,
            PasswordEncoder passwordEncoder,
            JdbcClient jdbc,
            AuditLogRepository auditLogs,
            PasswordPolicy passwordPolicy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.auditLogs = auditLogs;
        this.passwordPolicy = passwordPolicy;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(Principal principal, @Valid @RequestBody PasswordChangeRequest request) {
        var user = users.loadUserByUsername(principal.getName());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在のパスワードが正しくありません。");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在と異なるパスワードを指定してください。");
        }
        passwordPolicy.validate(request.newPassword(), principal.getName());
        jdbc.sql("""
                        UPDATE users
                           SET password = :password,
                               failed_login_count = 0,
                               locked_until = NULL,
                               must_change_password = FALSE,
                               password_changed_at = CURRENT_TIMESTAMP
                         WHERE username = :username
                        """)
                .param("password", passwordEncoder.encode(request.newPassword()))
                .param("username", principal.getName())
                .update();
        auditLogs.record(principal.getName(), "PASSWORD_CHANGED", "USER", principal.getName(), "");
    }
}
