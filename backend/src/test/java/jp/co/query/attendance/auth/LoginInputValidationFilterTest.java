package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginInputValidationFilterTest {

    private final LoginInputValidationFilter filter = new LoginInputValidationFilter();

    @Test
    void rejectsBlankUsernameWithJapaneseMessage() throws Exception {
        var request = loginRequest();
        request.addParameter("username", "");
        request.addParameter("password", "secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("ユーザーIDを入力してください。");
        verifyNoInteractions(chain);
    }

    @Test
    void passesValidCredentialsToAuthenticationFilter() throws Exception {
        var request = loginRequest();
        request.addParameter("username", "test");
        request.addParameter("password", "temporary-password");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest loginRequest() {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        return request;
    }
}
