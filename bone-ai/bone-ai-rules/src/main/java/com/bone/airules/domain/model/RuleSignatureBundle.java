package com.bone.airules.domain.model;

import java.util.Objects;

/**
 * 规则包双签名束：发布人与审批人各自签名（G12 控制矩阵「双角色分离」）。
 *
 * <p>两个签名者必须是不同身份；同一人既发布又审批属违规，验签器返回 REJECTED_SIGNER_NOT_DISTINCT，不放行。canonicalPayloadHash
 * 绑定被签内容，防签名与内容错配。
 *
 * @param publisherId 发布人身份
 * @param publisherSignature 发布人签名（base64）
 * @param approverId 审批人身份
 * @param approverSignature 审批人签名（base64）
 * @param algorithm 签名算法（须在白名单）
 * @param canonicalPayloadHash 规范化载荷的 sha256 小写十六进制
 */
public record RuleSignatureBundle(
    String publisherId,
    String publisherSignature,
    String approverId,
    String approverSignature,
    String algorithm,
    String canonicalPayloadHash) {

  public RuleSignatureBundle {
    Objects.requireNonNull(publisherId, "publisherId");
    Objects.requireNonNull(approverId, "approverId");
  }

  /** 双角色分离：发布人与审批人不得为同一身份。 */
  public boolean signersDistinct() {
    return !publisherId.equals(approverId);
  }
}
