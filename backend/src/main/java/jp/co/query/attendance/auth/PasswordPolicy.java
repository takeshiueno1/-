package jp.co.query.attendance.auth;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PasswordPolicy {

    private static final Set<String> BLOCKED_PASSWORDS = Set.of(
            "password", "password123", "password1234", "qwerty123456",
            "123456789012", "queryinsight", "attendance");

    public void validate(String password, String username) {
        if (password == null || !password.matches("[A-Za-z0-9]{8,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードは8～15文字の半角英数字で入力してください。");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (BLOCKED_PASSWORDS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "推測されやすいパスワードは使用できません。");
        }
    }
}
