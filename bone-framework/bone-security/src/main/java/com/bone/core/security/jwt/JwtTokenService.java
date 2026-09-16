package com.bone.core.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** 通用 JWT 签发与解析服务。各模块共用此实现。 */
@Component
public class JwtTokenService {

  private final JwtConfig jwtConfig;
  private final SecretKey signingKey;

  public JwtTokenService(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    assertJjwtCompatible();
    this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 启动期探测 jjwt 版本兼容性，把最难定位的运行期故障前置成一条可读错误（与 {@link JwtConfig#validate()} 同一思路）。
   *
   * <p><b>为什么必须探测</b>：本类按 jjwt 0.12.x 编译（{@code Jwts.parser()} 返回 {@code JwtParserBuilder}）。
   * 若消费方依赖树里 jjwt 被降到 0.11.x（典型成因：子模块在 pom 里<strong>硬编码</strong>版本——显式版本会覆盖 {@code bone-parent} 的
   * dependencyManagement），{@code Jwts.parser()} 会在<strong>运行期</strong>抛 {@code
   * NoSuchMethodError}。该症状还会被层层掩盖：鉴权失败返 401、Tomcat 转发 {@code /error} 又被鉴权拦成 401，排查者看到的是「带了有效 token
   * 仍 401」，几乎不会联想到依赖版本。
   */
  private static void assertJjwtCompatible() {
    try {
      // 仅探测方法能否解析，不使用返回值（无副作用）
      Jwts.parser();
    } catch (NoSuchMethodError | NoClassDefFoundError e) {
      throw new IllegalStateException(
          "jjwt 版本与 bone-security 不兼容：本类按 0.12.x 编译，当前 classpath 上是更低的版本。"
              + "请让 jjwt 由 bone-parent 的 dependencyManagement 统一管理（不要硬编码版本）；"
              + "定位来源可用 mvn dependency:tree -Dincludes=io.jsonwebtoken",
          e);
    }
  }

  /** 生成 JWT token（含 scopes）。 */
  public String generateToken(Long accountId, String username, Long tenantId, List<String> scopes) {
    List<String> safeScopes = scopes == null ? List.of() : scopes;
    return Jwts.builder()
        .subject(username)
        .claim("userId", String.valueOf(accountId))
        .claim("tenantId", String.valueOf(tenantId != null ? tenantId : 0L))
        .claim("scopes", safeScopes)
        .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
        .signWith(signingKey)
        .compact();
  }

  /** 解析 JWT 返回 JwtPrincipal。 */
  public Optional<JwtPrincipal> parse(String rawToken) {
    return parseClaims(rawToken).map(this::toPrincipal);
  }

  /** 解析 JWT 返回 Claims。 */
  public Optional<Claims> parseClaims(String rawToken) {
    try {
      String token = stripPrefix(rawToken);
      Claims payload =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      if (payload.getSubject() == null || payload.getSubject().isBlank()) {
        return Optional.empty();
      }
      return Optional.of(payload);
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private JwtPrincipal toPrincipal(Claims payload) {
    return new JwtPrincipal(
        payload.get("userId", String.class),
        payload.getSubject(),
        payload.get("tenantId", String.class),
        readScopes(payload));
  }

  @SuppressWarnings("unchecked")
  private static List<String> readScopes(Claims payload) {
    Object raw = payload.get("scopes");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return Collections.emptyList();
  }

  /** 去除 "Bearer " 前缀。 */
  public String stripBearerToken(String token) {
    return stripPrefix(token);
  }

  private String stripPrefix(String token) {
    if (token == null) return "";
    String prefix = jwtConfig.getTokenPrefix();
    if (prefix != null && !prefix.isBlank() && token.startsWith(prefix)) {
      return token.substring(prefix.length()).trim();
    }
    return token.trim();
  }
}
