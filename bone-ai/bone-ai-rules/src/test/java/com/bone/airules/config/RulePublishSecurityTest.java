package com.bone.airules.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airules.domain.gateway.RulePackageVerifier;
import com.bone.airules.domain.gateway.RulePublishAuditSink;
import com.bone.airules.domain.model.RulePublishAuditEvent;
import com.bone.airules.domain.model.RuleSignatureBundle;
import com.bone.airules.domain.model.VerificationResult;
import com.bone.airules.domain.service.RuleLoadCircuitBreaker;
import com.bone.airules.domain.service.RulePublishGuard;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** G12 安全门"能力就绪但未激活"实证：默认上下文零 bean、行为不变；显式启用才装配； 守卫对坏签名一律拒绝且发审计，熔断 fail-closed。 */
class RulePublishSecurityTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RulesAutoConfiguration.class));

  @Test
  void disabled_by_default_leaves_context_untouched() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(RulePackageVerifier.class);
          assertThat(ctx).doesNotHaveBean(RulePublishAuditSink.class);
          assertThat(ctx).doesNotHaveBean(RulePublishGuard.class);
          assertThat(ctx).doesNotHaveBean(RuleLoadCircuitBreaker.class);
        });
  }

  @Test
  void enabled_wires_all_g12_beans() {
    runner
        .withPropertyValues("bone.ai.rules.security.verify-enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(RulePackageVerifier.class);
              assertThat(ctx).hasSingleBean(RulePublishAuditSink.class);
              assertThat(ctx).hasSingleBean(RulePublishGuard.class);
              assertThat(ctx).hasSingleBean(RuleLoadCircuitBreaker.class);
            });
  }

  @Test
  void guard_rejects_bad_signature_and_still_emits_audit() {
    List<RulePublishAuditEvent> audit = new ArrayList<>();
    RulePackageVerifier rejecting = (payload, b, resolver) -> VerificationResult.REJECTED_SIGNATURE;
    RulePublishGuard guard = new RulePublishGuard(rejecting, audit::add);
    RuleSignatureBundle sigs =
        new RuleSignatureBundle("pub", "x", "app", "y", "SHA256withRSA", "h");

    RulePublishGuard.PublishDecision decision =
        guard.guard(
            "R1", 1, sigs, new byte[] {1}, true, (BiFunction<String, String, PublicKey>) null);

    assertThat(decision.admitted()).isFalse();
    assertThat(decision.code()).isEqualTo("REJECTED_SIGNATURE");
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).admitted()).isFalse();
  }

  @Test
  void guard_admits_only_valid_and_regression_passed() {
    List<RulePublishAuditEvent> audit = new ArrayList<>();
    RulePackageVerifier accepting = (payload, b, resolver) -> VerificationResult.ACCEPTED;
    RulePublishGuard guard = new RulePublishGuard(accepting, audit::add);
    RuleSignatureBundle sigs =
        new RuleSignatureBundle("pub", "x", "app", "y", "SHA256withRSA", "h");

    RulePublishGuard.PublishDecision ok = guard.guard("R1", 1, sigs, new byte[] {1}, true, null);
    RulePublishGuard.PublishDecision badRegression =
        guard.guard("R1", 2, sigs, new byte[] {1}, false, null);

    assertThat(ok.admitted()).isTrue();
    assertThat(ok.code()).isEqualTo("ADMITTED");
    assertThat(badRegression.admitted()).isFalse();
    assertThat(badRegression.code()).isEqualTo("REGRESSION_FAILED");
    assertThat(audit).hasSize(2);
  }

  @Test
  void circuit_breaker_fails_closed_and_resets() {
    RuleLoadCircuitBreaker cb = new RuleLoadCircuitBreaker();
    assertThat(cb.status()).isEqualTo("NORMAL");
    cb.reject("SIGNATURE_INVALID");
    assertThat(cb.isOpen()).isTrue();
    assertThat(cb.status()).isEqualTo("FAIL_CLOSED");
    assertThat(cb.reason()).isEqualTo("SIGNATURE_INVALID");
    cb.reset();
    assertThat(cb.status()).isEqualTo("NORMAL");
  }
}
