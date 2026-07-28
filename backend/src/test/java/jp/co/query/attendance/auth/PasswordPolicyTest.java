package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsEightAndFifteenAsciiAlphanumericCharacters() {
        assertThatCode(() -> policy.validate("ueno2026", "ueno"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("Abcdefghijk1234", "ueno"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsUsernameBasedPasswords() {
        assertThatCode(() -> policy.validate("ueno2026", "ueno"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOutOfRangeNonAsciiAlphanumericAndCommonPasswords() {
        assertThatThrownBy(() -> policy.validate("short7", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("Abcdefghijk12345", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("DemoUser-2026", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("Ａbcdefg1", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("password", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
