package jp.co.query.attendance.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    static final String INVALID_CREDENTIALS = "ユーザーIDまたはパスワードが正しくありません。";
    static final String DISABLED_CREDENTIALS = "認証情報が無効です。";

    private final LoginAccountStatusService accountStatus;

    public LoginFailureHandler(LoginAccountStatusService accountStatus) {
        this.accountStatus = accountStatus;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String message = accountStatus.isDisabled(request.getParameter("username"))
                ? DISABLED_CREDENTIALS
                : INVALID_CREDENTIALS;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
