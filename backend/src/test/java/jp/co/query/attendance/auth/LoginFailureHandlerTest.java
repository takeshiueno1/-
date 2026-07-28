package jp.co.query.attendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class LoginFailureHandlerTest {

    @Test
    void reportsDisabledCredentialsForDisabledAccount() throws Exception {
        LoginAccountStatusService accountStatus = mock(LoginAccountStatusService.class);
        when(accountStatus.isDisabled("disabled-user")).thenReturn(true);
        LoginFailureHandler handler = new LoginFailureHandler(accountStatus);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("username", "disabled-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("認証情報が無効です。");
    }

    @Test
    void keepsGenericMessageForUnknownOrIncorrectCredentials() throws Exception {
        LoginAccountStatusService accountStatus = mock(LoginAccountStatusService.class);
        LoginFailureHandler handler = new LoginFailureHandler(accountStatus);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("username", "unknown-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("ユーザーIDまたはパスワードが正しくありません。");
    }
}
