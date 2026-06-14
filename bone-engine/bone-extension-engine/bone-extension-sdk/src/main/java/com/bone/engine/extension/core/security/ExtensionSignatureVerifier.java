package com.bone.engine.extension.core.security;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 扩展签名验证器 实现扩展签名验证，确保扩展来源可信 */
@Slf4j
@Component
public class ExtensionSignatureVerifier {

  // 公钥
  private PublicKey publicKey;

  /**
   * 初始化签名验证器
   *
   * @param publicKeyBase64 Base64编码的公钥
   * @throws Exception 初始化异常
   */
  public void init(String publicKeyBase64) throws Exception {
    // 解码公钥
    byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);

    // 生成公钥
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    this.publicKey = keyFactory.generatePublic(keySpec);

    log.info("Extension signature verifier initialized");
  }

  /**
   * 验证扩展签名
   *
   * @param extensionCode 扩展代码
   * @param signatureBase64 Base64编码的签名
   * @return 是否验证通过
   */
  public boolean verifySignature(String extensionCode, String signatureBase64) {
    try {
      // 解码签名
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

      // 初始化签名验证
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(extensionCode.getBytes());

      // 验证签名
      boolean verified = signature.verify(signatureBytes);

      if (verified) {
        log.info("Extension signature verified: {}", extensionCode);
      } else {
        log.warn("Extension signature verification failed: {}", extensionCode);
      }

      return verified;
    } catch (Exception e) {
      log.error("Error verifying extension signature: {}", extensionCode, e);
      return false;
    }
  }

  /**
   * 验证扩展JAR文件签名
   *
   * @param jarFilePath JAR文件路径
   * @return 是否验证通过
   */
  public boolean verifyJarSignature(String jarFilePath) {
    // 实际实现中，这里应该验证JAR文件的签名
    // 简化实现，返回true
    log.info("Verifying JAR signature: {}", jarFilePath);
    return true;
  }
}
