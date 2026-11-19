package com.bone.airules.domain.service;

import com.bone.airules.domain.gateway.RulePackageVerifier;
import com.bone.airules.domain.gateway.RulePublishAuditSink;
import com.bone.airules.domain.model.RulePublishAuditEvent;
import com.bone.airules.domain.model.RuleSignatureBundle;
import com.bone.airules.domain.model.VerificationResult;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 规则发布守卫（G12 组合门）：验签 + 双角色 + 回归结论 + 审计，任一不过即 admitted=false。
 *
 * <p>被动能力类：默认不装配、不进入现有发布路径；是否启用由 bone.ai.rules.security.verify-enabled 决定。
 * 本类不产出可执行规则，仅给出准入判定与审计事件，资金权威路径不受影响。
 */
public class RulePublishGuard {

  private static final String ADMITTED = "ADMITTED";
  private static final String REGRESSION_FAILED = "REGRESSION_FAILED";

  private final RulePackageVerifier verifier;
  private final RulePublishAuditSink auditSink;

  public RulePublishGuard(RulePackageVerifier verifier, RulePublishAuditSink auditSink) {
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
  }

  /** 组合判定：双角色 → 验签 → 回归 → 审计，恒定发审计事件（含拒绝）。 */
  public PublishDecision guard(
      String ruleCode,
      int versionNo,
      RuleSignatureBundle sigs,
      byte[] canonicalPayload,
      boolean regressionPassed,
      BiFunction<String, String, PublicKey> keyResolver) {
    String code;
    boolean admitted = false;
    if (!sigs.signersDistinct()) {
      code = VerificationResult.REJECTED_SIGNER_NOT_DISTINCT.name();
    } else {
      VerificationResult vr = verifier.verify(canonicalPayload, sigs, keyResolver);
      if (vr != VerificationResult.ACCEPTED) {
        code = vr.name();
      } else if (!regressionPassed) {
        code = REGRESSION_FAILED;
      } else {
        code = ADMITTED;
        admitted = true;
      }
    }
    RulePublishAuditEvent event =
        new RulePublishAuditEvent(
            ruleCode, versionNo, sigs.publisherId(), code, admitted, Instant.now().toString());
    auditSink.record(event);
    return new PublishDecision(admitted, code, event);
  }

  /** 发布准入判定：admitted 为真才允许调用方继续走既有发布流程。 */
  public record PublishDecision(boolean admitted, String code, RulePublishAuditEvent auditEvent) {}
}
