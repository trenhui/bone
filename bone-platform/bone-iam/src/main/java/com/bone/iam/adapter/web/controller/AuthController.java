package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.application.usecase.standard.LoginUseCase;
import com.bone.iam.adapter.web.dto.req.LoginReq;
import com.bone.iam.adapter.web.dto.req.RefreshTokenReq;
import com.bone.iam.adapter.web.dto.resp.LoginResp;
import com.bone.iam.adapter.web.converter.AuthWebConverter;
import com.bone.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 提供登录、登出、刷新令牌、SSO相关接口
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class AuthController {
    private final LoginUseCase loginUseCase;
    private final AuthWebConverter authWebConverter;
    private final JwtTokenService jwtTokenService;

    /**
     * 用户登录
     * 验证用户名密码，生成JWT令牌和刷新令牌
     */
    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@RequestBody LoginReq req) {
        LoginCmd cmd = authWebConverter.toLoginCmd(req);
        Map<String, Object> result = loginUseCase.execute(cmd);
        LoginResp resp = authWebConverter.toLoginResp(result);
        return ApiResponse.success(resp);
    }

    /**
     * 用户登出
     * 将当前令牌加入黑名单
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() != null) {
            String token = auth.getCredentials().toString();
            jwtTokenService.addToBlacklist(token, 7200); // 2小时过期
        }
        return ApiResponse.success();
    }

    /**
     * 刷新令牌
     * 使用刷新令牌获取新的访问令牌
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResp> refreshToken(@RequestBody RefreshTokenReq req) {
        Map<String, Object> result = jwtTokenService.refreshToken(req.getRefreshToken());
        LoginResp resp = authWebConverter.toLoginResp(result);
        return ApiResponse.success(resp);
    }

    /**
     * 获取SSO配置
     */
    @GetMapping("/sso/config")
    public ApiResponse<Map<String, Object>> getSsoConfig() {
        Map<String, Object> config = Map.of(
            "enabled", false,
            "providers", new String[]{"oauth2", "saml", "ldap"}
        );
        return ApiResponse.success(config);
    }

    /**
     * SSO回调处理
     */
    @GetMapping("/sso/callback")
    public ApiResponse<LoginResp> ssoCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        // SSO回调处理逻辑
        return ApiResponse.success(null);
    }
}