package jp.co.query.attendance.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginInputValidationFilter extends OncePerRequestFilter {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,50}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equalsIgnoreCase("POST")
                || !request.getRequestURI().equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || username.isBlank()) {
            writeError(response, "ユーザーIDを入力してください。");
            return;
        }
        if (!USERNAME_PATTERN.matcher(username.strip().toLowerCase(java.util.Locale.ROOT)).matches()) {
            writeError(response, "ユーザーIDの形式を確認してください。");
            return;
        }
        if (password == null || password.isEmpty()) {
            writeError(response, "パスワードを入力してください。");
            return;
        }
        if (password.length() > 128) {
            writeError(response, "パスワードは128文字以内で入力してください。");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
