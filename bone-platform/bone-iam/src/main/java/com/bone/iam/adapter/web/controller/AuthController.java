package com.bone.iam.adapter.web.controller;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.AuthWebConverter;
import com.bone.iam.adapter.web.dto.request.LoginReq;
import com.bone.iam.adapter.web.dto.request.RefreshTokenReq;
import com.bone.iam.adapter.web.dto.response.LoginResp;
import com.bone.iam.application.AuthApplicationService;
import com.bone.iam.application.command.LoginCommand;
import com.bone.iam.application.command.RefreshTokenCommand;
import com.bone.iam.application.config.IamSsoProperties;
import com.bone.iam.common.IamErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p>仅依赖 application 层（{@link AuthApplicationService}、{@link IamSsoProperties} 已按 E-10.2 落在 {@code
 * application.config}）与框架安全配置（{@link JwtConfig}）——不再注入任何 {@code infrastructure} 类（E-10.1）。
 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1)
@RequiredArgsConstructor
public class AuthController {

  private final AuthApplicationService authApplicationService;
  private final AuthWebConverter authWebConverter;
  private final JwtConfig jwtConfig;
  private final IamSsoProperties iamSsoProperties;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResp>> login(
      @RequestBody LoginReq req, HttpServletRequest request) {
    LoginCommand cmd = authWebConverter.toLoginCommand(req, resolveClientIp(request));
    try {
      Map<String, Object> result = authApplicationService.login(cmd);
      return ResponseEntity.ok(ApiResponse.success(authWebConverter.toLoginResp(result)));
    } catch (BizException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }
  }

  /** 优先 {@code X-Forwarded-For} 链首段，回退到 {@link HttpServletRequest#getRemoteAddr()}（含 IPv6）。 */
  private static String resolveClientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
    }
    return request.getRemoteAddr();
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(HttpServletRequest request) {
    authApplicationService.logout(request.getHeader(jwtConfig.getHeaderName()));
    return ApiResponse.success();
  }

  @PostMapping("/refresh")
  public ApiResponse<Map<String, String>> refreshToken(@RequestBody RefreshTokenReq req) {
    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken(req.getRefreshToken());
    return ApiResponse.success(authApplicationService.refreshToken(cmd));
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

  // 能力未上线：SSO IdP 回调尚未实现，显式返回 501（SSO_NOT_CONFIGURED）。前端不应暴露该回调入口。
  @Deprecated(since = "vision", forRemoval = false)
  @GetMapping("/sso/callback")
  public ResponseEntity<ApiResponse<LoginResp>> ssoCallback(
      @RequestParam String code, @RequestParam(required = false) String state) {
    if (!iamSsoProperties.isEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
          .body(
              ApiResponse.error(
                  501,
                  IamErrorCodes.SSO_NOT_CONFIGURED + ": SSO 未配置，请设置 bone.iam.sso.enabled=true"));
    }
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(ApiResponse.error(501, IamErrorCodes.SSO_NOT_CONFIGURED + ": IdP 回调处理尚未实现"));
  }
}
