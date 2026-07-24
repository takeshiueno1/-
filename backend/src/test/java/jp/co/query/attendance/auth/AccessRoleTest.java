package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AccessRoleTest {

    @Test
    void threeRolesHaveExpectedHierarchy() {
        assertThat(AccessRole.USER.springRoles()).containsExactly("USER");
        assertThat(AccessRole.MANAGER.springRoles()).containsExactly("USER", "MANAGER");
        assertThat(AccessRole.ADMIN.springRoles()).containsExactly("USER", "MANAGER", "ADMIN");
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> AccessRole.from("OWNER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("権限は一般、役職者、管理者");
    }
}
