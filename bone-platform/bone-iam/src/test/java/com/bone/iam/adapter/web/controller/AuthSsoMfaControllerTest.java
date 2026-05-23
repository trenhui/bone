package com.bone.iam.adapter.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.application.command.handler.LoginHandler;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.infrastructure.config.IamSsoProperties;
import com.bone.iam.infrastructure.config.JwtConfig;
import com.bone.iam.infrastructure.security.JwtTokenService;
import com.bone.iam.infrastructure.security.RefreshTokenService;
import com.bone.iam.infrastructure.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthSsoMfaControllerTest {

    @Mock
    private LoginHandler loginHandler;

    @Mock
    private com.bone.iam.adapter.web.converter.AuthWebConverter authWebConverter;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;

    @Mock
    private JwtConfig jwtConfig;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSsoProperties ssoProperties = new IamSsoProperties();
        AuthController authController =
                new AuthController(
                        loginHandler,
                        authWebConverter,
                        jwtTokenService,
                        refreshTokenService,
                        tokenBlacklistService,
                        accountRepository,
                        accountAuthoritiesQueryHandler,
                        jwtConfig,
                        ssoProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(authController, new MfaController()).build();
    }

    @Test
    void ssoConfigReturnsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/iam/sso/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void ssoCallbackReturns501WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/iam/sso/callback").param("code", "test-code"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.message", containsString("IAM_SSO_NOT_CONFIGURED")));
    }

    @Test
    void mfaStatusReturnsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/iam/mfa/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.enrolled").value(false));
    }

    @Test
    void mfaEnrollReturns501() throws Exception {
        mockMvc.perform(post("/api/v1/iam/mfa/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value(501));
    }
}
