package com.bone.core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 验证 ADR-0005 双轨：HS256 与 RS256 同时可验，且错误密钥的 RS256 token 被拒。 */
class RsaJwtTokenServiceTest {

  private static String toPem(String type, byte[] der) {
    String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
    return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
  }

  private JwtTokenService serviceWithRsa() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair kp = gen.generateKeyPair();
    JwtConfig cfg = new JwtConfig(new MockEnvironment());
    cfg.setSecretKey("change-me-change-me-change-me-change-me-32bytes");
    cfg.setRsaPublicKeyPem(toPem("PUBLIC KEY", kp.getPublic().getEncoded()));
    cfg.setRsaPrivateKeyPem(toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
    cfg.setExpirationMs(60_000L);
    return new JwtTokenService(cfg);
  }

  @Test
  void hs256StillVerifies() throws Exception {
    JwtTokenService svc = serviceWithRsa();
    String token = svc.generateToken(1L, "alice", 1001L, List.of("order:read"));
    Optional<JwtPrincipal> p = svc.parse(token);
    assertTrue(p.isPresent());
    assertEquals("alice", p.get().username());
    assertEquals("1001", p.get().tenantId());
  }

  @Test
  void rs256TokenVerifiesViaDualMode() throws Exception {
    JwtTokenService svc = serviceWithRsa();
    String token = svc.generateRsaToken(2L, "bob", 2002L, List.of("order:write"));
    Optional<JwtPrincipal> p = svc.parse(token);
    assertTrue(p.isPresent(), "RS256 token 应被双模验签接受");
    assertEquals("bob", p.get().username());
    assertEquals("2002", p.get().tenantId());
  }

  @Test
  void rs256TokenSignedByWrongKeyIsRejected() throws Exception {
    JwtTokenService svc = serviceWithRsa();
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair other = gen.generateKeyPair();
    JwtConfig evil = new JwtConfig(new MockEnvironment());
    evil.setSecretKey("change-me-change-me-change-me-change-me-32bytes");
    evil.setRsaPublicKeyPem(toPem("PUBLIC KEY", other.getPublic().getEncoded()));
    evil.setRsaPrivateKeyPem(toPem("PRIVATE KEY", other.getPrivate().getEncoded()));
    evil.setExpirationMs(60_000L);
    String evilToken =
        new JwtTokenService(evil).generateRsaToken(9L, "mallory", 9999L, List.of("admin"));
    // svc 持正确公钥，应拒绝用 other 私钥签的 token
    assertFalse(svc.parse(evilToken).isPresent());
  }
}
