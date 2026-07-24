package jp.co.query.attendance.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcClient jdbc;
    private final PasswordLifecycleService passwordLifecycle;

    public AuthController(JdbcClient jdbc, PasswordLifecycleService passwordLifecycle) {
        this.jdbc = jdbc;
        this.passwordLifecycle = passwordLifecycle;
    }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication, CsrfToken csrfToken) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        boolean initialPasswordChangeRequired = authenticated && jdbc.sql("""
                        SELECT must_change_password
                          FROM users
                         WHERE username = :username
                        """)
                .param("username", authentication.getName())
                .query(Boolean.class)
                .optional()
                .orElse(false);
        PasswordLifecycleService.Status lifecycle = authenticated
                ? passwordLifecycle.find(authentication.getName())
                : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", authenticated);
        result.put("username", authenticated ? authentication.getName() : "");
        result.put("roles", authenticated
                ? authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).sorted().toList()
                : List.of());
        result.put("mustChangePassword",
                initialPasswordChangeRequired || lifecycle != null && lifecycle.expired());
        result.put("passwordExpired", lifecycle != null && lifecycle.expired());
        result.put("passwordExpiryWarning", lifecycle != null && lifecycle.warning());
        result.put("passwordExpiryDaysRemaining", lifecycle == null ? null : lifecycle.daysRemaining());
        result.put("passwordExpiresAt", lifecycle == null ? null : lifecycle.expiresAt());
        result.put("csrfToken", csrfToken.getToken());
        result.put("csrfHeaderName", csrfToken.getHeaderName());
        return result;
    }
}
