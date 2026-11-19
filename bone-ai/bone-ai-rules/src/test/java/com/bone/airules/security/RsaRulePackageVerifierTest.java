package com.bone.airules.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airules.domain.model.RuleSignatureBundle;
import com.bone.airules.domain.model.VerificationResult;
import com.bone.airules.infrastructure.security.RsaRulePackageVerifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 验签器判别力：合法双签放行，任一伪造/错配/越权都被精确拒绝（fail-closed）。 */
class RsaRulePackageVerifierTest {

  private static final String ALG = "SHA256withRSA";
  private final RsaRulePackageVerifier verifier = new RsaRulePackageVerifier();
  private KeyPair publisher;
  private KeyPair approver;
  private byte[] payload;
  private String payloadHash;
  private BiFunction<String, String, PublicKey> keys;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    publisher = gen.generateKeyPair();
    approver = gen.generateKeyPair();
    payload = "{\"expression\":\"amount>100\"}".getBytes(StandardCharsets.UTF_8);
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b & 0xff));
    }
    payloadHash = sb.toString();
    keys =
        (signer, alg) -> {
          if ("pub".equals(signer)) {
            return publisher.getPublic();
          }
          if ("app".equals(signer)) {
            return approver.getPublic();
          }
          return null;
        };
  }

  private String sign(KeyPair pair, byte[] data) throws Exception {
    Signature sig = Signature.getInstance(ALG);
    sig.initSign(pair.getPrivate());
    sig.update(data);
    return Base64.getEncoder().encodeToString(sig.sign());
  }

  private RuleSignatureBundle bundle(String pubSig, String appSig) throws Exception {
    return new RuleSignatureBundle("pub", pubSig, "app", appSig, ALG, payloadHash);
  }

  @Test
  void valid_dual_signature_is_accepted() throws Exception {
    RuleSignatureBundle b = bundle(sign(publisher, payload), sign(approver, payload));
    assertThat(verifier.verify(payload, b, keys)).isEqualTo(VerificationResult.ACCEPTED);
  }

  @Test
  void tampered_payload_is_rejected_by_hash() throws Exception {
    RuleSignatureBundle b = bundle(sign(publisher, payload), sign(approver, payload));
    byte[] tampered = "{\"expression\":\"amount>1\"}".getBytes(StandardCharsets.UTF_8);
    assertThat(verifier.verify(tampered, b, keys))
        .isEqualTo(VerificationResult.REJECTED_PAYLOAD_HASH);
  }

  @Test
  void broken_signature_is_rejected() throws Exception {
    RuleSignatureBundle b = bundle(sign(publisher, payload) + "broken", sign(approver, payload));
    assertThat(verifier.verify(payload, b, keys)).isEqualTo(VerificationResult.REJECTED_SIGNATURE);
  }

  @Test
  void same_signer_for_both_roles_is_rejected() throws Exception {
    RuleSignatureBundle b =
        new RuleSignatureBundle(
            "pub", sign(publisher, payload), "pub", sign(publisher, payload), ALG, payloadHash);
    assertThat(verifier.verify(payload, b, keys))
        .isEqualTo(VerificationResult.REJECTED_SIGNER_NOT_DISTINCT);
  }

  @Test
  void unknown_key_is_rejected() throws Exception {
    RuleSignatureBundle b =
        new RuleSignatureBundle(
            "ghost", sign(publisher, payload), "app", sign(approver, payload), ALG, payloadHash);
    assertThat(verifier.verify(payload, b, keys)).isEqualTo(VerificationResult.REJECTED_SIGNATURE);
  }

  @Test
  void unknown_algorithm_is_rejected() throws Exception {
    RuleSignatureBundle b =
        new RuleSignatureBundle(
            "pub",
            sign(publisher, payload),
            "app",
            sign(approver, payload),
            "MD5withRSA",
            payloadHash);
    assertThat(verifier.verify(payload, b, keys)).isEqualTo(VerificationResult.REJECTED_ALGORITHM);
  }
}
