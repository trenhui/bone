package com.bone.aiintegration.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HmacCallbackVerifierTest {

  private static final String SECRET = "cb-secret";
  private final HmacCallbackVerifier verifier = new HmacCallbackVerifier(SECRET);
  private final String payload = "{\"claimNo\":\"C1\",\"result\":\"PAID\"}";

  private String sign(String body) {
    return HmacCallbackVerifier.hmacHex(body, SECRET);
  }

  @Test
  void accepts_valid_signature() {
    assertThatCode(() -> verifier.verify(payload, sign(payload))).doesNotThrowAnyException();
  }

  @Test
  void accepts_prefixed_and_uppercase_signature() {
    String sig = "sha256=" + sign(payload).toUpperCase();
    assertThatCode(() -> verifier.verify(payload, sig)).doesNotThrowAnyException();
  }

  @Test
  void rejects_tampered_payload() {
    String sig = sign(payload);
    assertThatThrownBy(() -> verifier.verify(payload + " ", sig)) // 改一个字节
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void rejects_wrong_signature() {
    assertThatThrownBy(() -> verifier.verify(payload, "deadbeef"))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void rejects_missing_signature_fail_closed() {
    assertThatThrownBy(() -> verifier.verify(payload, null)).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> verifier.verify(payload, "  ")).isInstanceOf(SecurityException.class);
  }

  @Test
  void rejects_signature_from_different_secret() {
    String forged = HmacCallbackVerifier.hmacHex(payload, "other-secret");
    assertThatThrownBy(() -> verifier.verify(payload, forged))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void blank_secret_is_rejected_at_construction() {
    assertThatThrownBy(() -> new HmacCallbackVerifier("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
