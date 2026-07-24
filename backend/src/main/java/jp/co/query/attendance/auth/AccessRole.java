package jp.co.query.attendance.auth;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum AccessRole {
    USER("一般", List.of("USER")),
    MANAGER("役職者", List.of("USER", "MANAGER")),
    ADMIN("管理者", List.of("USER", "MANAGER", "ADMIN"));

    private final String displayName;
    private final List<String> springRoles;

    AccessRole(String displayName, List<String> springRoles) {
        this.displayName = displayName;
        this.springRoles = springRoles;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> springRoles() {
        return springRoles;
    }

    public static AccessRole from(String value) {
        try {
            return AccessRole.valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "権限は一般、役職者、管理者から選択してください。",
                    exception);
        }
    }
}
