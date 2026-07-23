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
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードは12～128文字で入力してください。");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (BLOCKED_PASSWORDS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "推測されやすいパスワードは使用できません。");
        }
        if (username != null && username.length() >= 3
                && normalized.contains(username.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ユーザー名を含むパスワードは使用できません。");
        }
    }
}
