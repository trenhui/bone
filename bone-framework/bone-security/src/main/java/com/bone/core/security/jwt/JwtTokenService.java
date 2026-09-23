package com.bone.core.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
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
  private final PublicKey rsaPublicKey;
  private final PrivateKey rsaPrivateKey;

  public JwtTokenService(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    assertJjwtCompatible();
    this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8));
    this.rsaPublicKey =
        jwtConfig.getRsaPublicKeyPem() != null && !jwtConfig.getRsaPublicKeyPem().isBlank()
            ? RsaKeyParser.publicKeyFromPem(jwtConfig.getRsaPublicKeyPem())
            : null;
    this.rsaPrivateKey =
        jwtConfig.getRsaPrivateKeyPem() != null && !jwtConfig.getRsaPrivateKeyPem().isBlank()
            ? RsaKeyParser.privateKeyFromPem(jwtConfig.getRsaPrivateKeyPem())
            : null;
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

  /** 生成 JWT token（含 scopes）。HS256 对称签发，现有默认行为，保持不变。 */
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

  /**
   * 生成 RS256 非对称 token（ADR-0005 对齐）。仅当配置了 RSA 私钥时可用；默认不启用， 须由 IAM 在双轨窗口内显式开启 {@code
   * rsaIssuanceEnabled} 后调用。
   */
  public String generateRsaToken(
      Long accountId, String username, Long tenantId, List<String> scopes) {
    if (rsaPrivateKey == null) {
      throw new IllegalStateException(
          "RS256 签发未配置（bone.iam.jwt.rsa-private-key-pem 缺失），无法签发 RS256 token");
    }
    List<String> safeScopes = scopes == null ? List.of() : scopes;
    return Jwts.builder()
        .header()
        .add("kid", jwtConfig.getRsaKeyId())
        .and()
        .subject(username)
        .claim("userId", String.valueOf(accountId))
        .claim("tenantId", String.valueOf(tenantId != null ? tenantId : 0L))
        .claim("scopes", safeScopes)
        .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
        .signWith(rsaPrivateKey)
        .compact();
  }

  /** 解析 JWT 返回 JwtPrincipal。 */
  public Optional<JwtPrincipal> parse(String rawToken) {
    return parseClaims(rawToken).map(this::toPrincipal);
  }

  /**
   * 解析 JWT 返回 Claims。双模验签（ADR-0005 双轨过渡）： 先 HS256（现有默认），失败再回退 RS256（若配置了公钥）。任一成功即返回，
   * 二者皆失败或均未配置则返回空。不改变现有 HS256 行为。
   */
  public Optional<Claims> parseClaims(String rawToken) {
    Optional<Claims> hmac = tryParseHmac(rawToken);
    if (hmac.isPresent()) {
      return hmac;
    }
    return tryParseRsa(rawToken);
  }

  private Optional<Claims> tryParseHmac(String rawToken) {
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

  private Optional<Claims> tryParseRsa(String rawToken) {
    if (rsaPublicKey == null) {
      return Optional.empty();
    }
    try {
      String token = stripPrefix(rawToken);
      Claims payload =
          Jwts.parser().verifyWith(rsaPublicKey).build().parseSignedClaims(token).getPayload();
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
