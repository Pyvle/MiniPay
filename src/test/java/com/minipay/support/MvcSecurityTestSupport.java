package com.minipay.support;

import com.minipay.auth.security.RestAccessDeniedHandler;
import com.minipay.auth.security.RestAuthenticationEntryPoint;
import com.minipay.auth.security.SecurityErrorWriter;
import com.minipay.auth.token.SessionJwtAuthenticationConverter;
import com.minipay.config.SecurityConfig;
import com.minipay.session.SessionAuthenticationService;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Controller slice tests exercise business responses after authentication.
 * Real JWT/session authentication is covered by CurrentUserSecurityIntegrationTest.
 */
@Import({SecurityConfig.class, SessionJwtAuthenticationConverter.class,
        SecurityErrorWriter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@WithMockUser(username = "1")
public abstract class MvcSecurityTestSupport {
    @MockitoBean protected SessionAuthenticationService sessionAuthenticationService;
    @MockitoBean protected JwtDecoder jwtDecoder;
}
