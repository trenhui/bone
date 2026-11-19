package com.bone.airules.domain.gateway;

import com.bone.airules.domain.model.RuleSignatureBundle;
import com.bone.airules.domain.model.VerificationResult;
import java.security.PublicKey;
import java.util.function.BiFunction;

/**
 * 规则包验签端口（G12 双签名验签）：验签算法与密钥解析由基础设施注入，domain 只认契约。
 *
 * <p>keyResolver 入参为（签名人身份, 算法），返回该身份的公钥；返回 null 视为密钥未知，验签器须 fail-closed。
 */
public interface RulePackageVerifier {

  VerificationResult verify(
      byte[] canonicalPayload,
      RuleSignatureBundle bundle,
      BiFunction<String, String, PublicKey> keyResolver);
}
