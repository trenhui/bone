package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.AuthWebConverter;
import com.bone.iam.adapter.web.dto.req.LoginReq;
import com.bone.iam.adapter.web.dto.req.RefreshTokenReq;
import com.bone.iam.adapter.web.dto.resp.LoginResp;
import com.bone.iam.application.command.cmd.LoginCommand;
import com.bone.iam.application.command.handler.LoginHandler;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.infrastructure.config.IamSsoProperties;
import com.bone.iam.infrastructure.config.JwtConfig;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.infrastructure.security.JwtTokenService;
import java.util.List;
import com.bone.iam.infrastructure.security.RefreshTokenService;
import com.bone.iam.infrastructure.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：登录、登出、刷新令牌。
 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1)
@RequiredArgsConstructor
public class AuthController {

    private final LoginHandler loginHandler;
    private final AuthWebConverter authWebConverter;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AccountRepository accountRepository;
    private final AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;
    private final JwtConfig jwtConfig;
    private final IamSsoProperties iamSsoProperties;

    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@RequestBody LoginReq req) {
        LoginCommand cmd = authWebConverter.toLoginCommand(req);
        Map<String, Object> result = loginHandler.handle(cmd);
        return ApiResponse.success(authWebConverter.toLoginResp(result));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(jwtConfig.getHeaderName());
        if (header != null && !header.isBlank()) {
            jwtTokenService.parse(header).ifPresent(principal -> {
                String raw = jwtTokenService.stripBearerToken(header);
                tokenBlacklistService.blacklist(raw, Duration.ofMillis(jwtConfig.getExpirationMs()));
            });
        }
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refreshToken(@RequestBody RefreshTokenReq req) {
        Map<String, String> rotated = refreshTokenService.rotate(req.getRefreshToken());
        long accountId = Long.parseLong(rotated.get("accountId"));
        var account = accountRepository.findById(accountId);
        if (account == null) {
            return ApiResponse.error(401, "账户不存在或已禁用");
        }
        List<String> scopes = accountAuthoritiesQueryHandler.resolvePermissionCodes(
                account.getId(), account.isAdmin());
        String accessToken = jwtTokenService.generateToken(
                account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
        return ApiResponse.success(Map.of(
                "accessToken", accessToken,
                "refreshToken", rotated.get("refreshToken")));
    }

    @GetMapping("/sso/config")
    public ApiResponse<Map<String, Object>> getSsoConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", iamSsoProperties.isEnabled());
        config.put("provider", iamSsoProperties.getProvider());
        config.put("authorizationUrl", iamSsoProperties.getAuthorizationUrl());
        config.put("clientId", iamSsoProperties.getClientId());
        config.put("supportedProviders", List.of("oauth2", "saml", "ldap"));
        return ApiResponse.success(config);
    }

    @GetMapping("/sso/callback")
    public ResponseEntity<ApiResponse<LoginResp>> ssoCallback(
            @RequestParam String code, @RequestParam(required = false) String state) {
        if (!iamSsoProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ApiResponse.error(
                            501,
                            IamErrorCodes.SSO_NOT_CONFIGURED + ": SSO 未配置，请设置 bone.iam.sso.enabled=true"));
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(501, IamErrorCodes.SSO_NOT_CONFIGURED + ": IdP 回调处理尚未实现"));
    }
}
