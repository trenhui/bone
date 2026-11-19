package com.bone.airules.infrastructure.security;

import com.bone.airules.domain.gateway.RulePackageVerifier;
import com.bone.airules.domain.model.RuleSignatureBundle;
import com.bone.airules.domain.model.VerificationResult;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * RSA 规则包双签名验签器：仅用 JDK java.security（无新依赖）。
 *
 * <p>任一签名无效 / 密钥缺失 / 算法不在白名单 / 载荷哈希错配 / 两签名同一人 ⇒ 返回对应 REJECTED， 不抛异常、不放行（fail-closed）。默认不被装配，仅
 * verify-enabled=true 时由自动配置注册。
 */
public class RsaRulePackageVerifier implements RulePackageVerifier {

  private static final Set<String> ALLOWED_ALGORITHMS = Set.of("SHA256withRSA", "SHA512withRSA");

  @Override
  public VerificationResult verify(
      byte[] canonicalPayload,
      RuleSignatureBundle bundle,
      BiFunction<String, String, PublicKey> keyResolver) {
    if (bundle == null || canonicalPayload == null || keyResolver == null) {
      return VerificationResult.REJECTED_SIGNATURE;
    }
    if (!bundle.signersDistinct()) {
      return VerificationResult.REJECTED_SIGNER_NOT_DISTINCT;
    }
    if (!ALLOWED_ALGORITHMS.contains(bundle.algorithm())) {
      return VerificationResult.REJECTED_ALGORITHM;
    }
    String actualHash = sha256Hex(canonicalPayload);
    if (!actualHash.equalsIgnoreCase(bundle.canonicalPayloadHash())) {
      return VerificationResult.REJECTED_PAYLOAD_HASH;
    }
    if (!verifyOne(
        canonicalPayload,
        bundle.publisherSignature(),
        bundle.publisherId(),
        bundle.algorithm(),
        keyResolver)) {
      return VerificationResult.REJECTED_SIGNATURE;
    }
    if (!verifyOne(
        canonicalPayload,
        bundle.approverSignature(),
        bundle.approverId(),
        bundle.algorithm(),
        keyResolver)) {
      return VerificationResult.REJECTED_SIGNATURE;
    }
    return VerificationResult.ACCEPTED;
  }

  private boolean verifyOne(
      byte[] payload,
      String signatureB64,
      String signerId,
      String algorithm,
      BiFunction<String, String, PublicKey> keyResolver) {
    if (signatureB64 == null || signatureB64.isBlank()) {
      return false;
    }
    PublicKey key = keyResolver.apply(signerId, algorithm);
    if (key == null) {
      return false;
    }
    try {
      Signature sig = Signature.getInstance(algorithm);
      sig.initVerify(key);
      sig.update(payload);
      return sig.verify(Base64.getDecoder().decode(signatureB64));
    } catch (Exception ex) {
      return false;
    }
  }

  private static String sha256Hex(byte[] payload) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
