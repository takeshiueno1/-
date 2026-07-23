package jp.co.query.attendance.auth;

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

    public AuthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication, CsrfToken csrfToken) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        boolean mustChangePassword = authenticated && jdbc.sql("""
                        SELECT must_change_password
                          FROM users
                         WHERE username = :username
                        """)
                .param("username", authentication.getName())
                .query(Boolean.class)
                .optional()
                .orElse(false);
        return Map.of(
                "authenticated", authenticated,
                "username", authenticated ? authentication.getName() : "",
                "roles", authenticated
                        ? authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).sorted().toList()
                        : java.util.List.of(),
                "mustChangePassword", mustChangePassword,
                "csrfToken", csrfToken.getToken(),
                "csrfHeaderName", csrfToken.getHeaderName());
    }
}
