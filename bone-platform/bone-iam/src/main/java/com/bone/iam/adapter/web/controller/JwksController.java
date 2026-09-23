package com.bone.iam.adapter.web.controller;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.RsaKeyParser;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWKS 端点（ADR-0005）。暴露 IAM 的 RS256 公钥，供网关与各消费方拉取并验签 RS256 token。
 *
 * <p>仅在配置了 {@code bone.iam.jwt.rsa-public-key-pem} 时才生效；未配置 RS256 时返回 404， 表明当前仍为纯 HS256，消费方无需变更。
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class JwksController {

  private final JwtConfig jwtConfig;

  @GetMapping("/jwks.json")
  public ResponseEntity<Map<String, Object>> jwks() {
    String pem = jwtConfig.getRsaPublicKeyPem();
    if (pem == null || pem.isBlank()) {
      return ResponseEntity.notFound().build();
    }
    RSAPublicKey pub = (RSAPublicKey) RsaKeyParser.publicKeyFromPem(pem);
    Map<String, Object> jwk = new LinkedHashMap<>();
    jwk.put("kty", "RSA");
    jwk.put("use", "sig");
    jwk.put("kid", jwtConfig.getRsaKeyId());
    jwk.put("alg", "RS256");
    jwk.put("n", base64Url(minimal(pub.getModulus().toByteArray())));
    jwk.put("e", base64Url(minimal(pub.getPublicExponent().toByteArray())));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("keys", new Object[] {jwk});
    return ResponseEntity.ok(body);
  }

  private static byte[] minimal(byte[] bytes) {
    if (bytes.length > 1 && bytes[0] == 0) {
      return Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return bytes;
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
