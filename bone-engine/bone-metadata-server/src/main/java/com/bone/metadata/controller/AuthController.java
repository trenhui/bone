package com.bone.metadata.controller;

import com.bone.metadata.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.*;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** 登录示例控制器：前端通过用户名/密码获取 JWT 生产环境请对接 UserDetailsService 或 OAuth2 授权服务器 登录认证（示例：JWT 颁发） */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

  private final JwtUtil jwtUtil;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public AuthController(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Operation(
      summary = "用户登录，获取 JWT",
      responses = {
        @ApiResponse(responseCode = "200", description = "登录成功，返回 token"),
        @ApiResponse(responseCode = "401", description = "用户名或密码错误")
      })
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
    // 示例：硬编码 admin/password，生产请对接真实用户系统
    String hardcodedUser = "admin";
    String hardcodedPasswordHash = passwordEncoder.encode("password"); // 仅演示

    if (hardcodedUser.equals(req.getUsername())
        && passwordEncoder.matches(req.getPassword(), hardcodedPasswordHash)) {
      Map<String, Object> claims = new HashMap<>();
      // 携带权限范围（roles 或 scopes）
      claims.put("roles", List.of("metadata:read", "metadata:write"));
      String token = jwtUtil.generateToken(req.getUsername(), claims);
      return ResponseEntity.ok(new LoginResponse(token));
    }
    return ResponseEntity.status(401).build();
  }

  @Data
  static class LoginRequest {
    private String username;
    private String password;
  }

  @Data
  static class LoginResponse {
    private final String access_token;
    private final String token_type = "Bearer";
    private final long expires_in = 3600; // 秒
  }
}
