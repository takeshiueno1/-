package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongPassphraseWithoutCompositionRequirement() {
        assertThatCode(() -> policy.validate("correct horse battery staple", "ueno"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortCommonAndUsernameBasedPasswords() {
        assertThatThrownBy(() -> policy.validate("short", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("password1234", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("Secure-ueno-2026!", "ueno"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
