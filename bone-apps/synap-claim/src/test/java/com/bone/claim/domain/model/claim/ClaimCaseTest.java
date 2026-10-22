package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ClaimCase 聚合根领域单测（状态机合法转换 + 不变量 + 事件）。 */
class ClaimCaseTest {

  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  private static ClaimCase newClaim() {
    return ClaimCase.draft(
        1001L,
        42L,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        OUT_INSURE,
        "B20260312",
        "v3",
        List.of(
            ClaimItem.of(
                2001L,
                1001L,
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"))));
  }

  @Nested
  @DisplayName("受理与初审链")
  class PreExamChain {

    @Test
    @DisplayName("报案即受理：DRAFT→7，幂等重复 accept 返回 false")
    void acceptIsIdempotent() {
      ClaimCase claim = newClaim();
      assertThat(claim.status()).isEqualTo(ClaimStatus.DRAFT);
      assertThat(claim.accept()).isTrue();
      assertThat(claim.status()).isEqualTo(ClaimStatus.ACCEPTED);
      assertThat(claim.externalStatus()).isEqualTo("PENDING_EVIDENCE");
      assertThat(claim.accept()).isFalse();
      // 受理 = 状态跃迁事件 + ClaimSubmittedEvent 业务事件（两个都发）
      assertThat(claim.getDomainEvents()).hasSize(2);
      assertThat(claim.getDomainEvents())
          .anyMatch(e -> e instanceof com.bone.claim.domain.model.claim.event.ClaimSubmittedEvent);
    }

    @Test
    @DisplayName("非草稿不可受理")
    void acceptRejectsNonDraft() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.allocateToPreExam("op-1");
      assertThatThrownBy(claim::accept).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("7→10→13→12 自动化初审链")
    void automatedChain() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.allocateToPreExam("op-1");
      claim.startPreExam(true, "SYSTEM");
      assertThat(claim.status()).isEqualTo(ClaimStatus.AUTO_PRE_EXAMINING);
      claim.completePreExam("SYSTEM");
      assertThat(claim.status()).isEqualTo(ClaimStatus.PRE_EXAMINED);
      assertThat(claim.externalStatus()).isEqualTo("REVIEWING");
    }

    @Test
    @DisplayName("补件流转 7→21→10")
    void evidenceLoop() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.requestEvidence("op-1");
      assertThat(claim.status()).isEqualTo(ClaimStatus.EVIDENCE_SUBMITTING);
      assertThat(claim.externalStatus()).isEqualTo("PENDING_EVIDENCE");
      claim.evidenceSubmitted("op-1");
      assertThat(claim.status()).isEqualTo(ClaimStatus.PENDING_PRE_EXAM);
    }
  }

  @Nested
  @DisplayName("人工核赔决策")
  class AdjustDecision {

    private ClaimCase atAdjusting() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.allocateToPreExam("op-1");
      claim.startPreExam(false, "op-1");
      claim.completePreExam("op-1");
      claim.escalateToAdjust("op-1");
      claim.claimAdjustTask("reviewer-1");
      return claim;
    }

    @Test
    @DisplayName("通过：41→42，核定金额 ≤ 申请金额")
    void approve() {
      ClaimCase claim = atAdjusting();
      claim.decide(Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("10.00"));
      assertThat(claim.status()).isEqualTo(ClaimStatus.ADJUSTED);
      assertThat(claim.getApprovedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("核定金额超过申请金额被拒（不变量）")
    void approveRejectsOverAmount() {
      ClaimCase claim = atAdjusting();
      assertThatThrownBy(
              () ->
                  claim.decide(
                      Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("11.00")))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("不能超过申请金额");
    }

    @Test
    @DisplayName("拒赔必填五要素 + 受控原因码：41→45")
    void rejectRequiresReason() {
      ClaimCase claim = atAdjusting();
      assertThatThrownBy(() -> claim.decide(Decision.REJECTED, "reviewer-1", "  ", "03", null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("五要素");
      assertThatThrownBy(
              () -> claim.decide(Decision.REJECTED, "reviewer-1", "事实+条款+证据+渠道+时限", null, null))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("受控原因码");
      claim.decide(Decision.REJECTED, "reviewer-1", "事实+条款+证据+渠道+时限", "03", null);
      assertThat(claim.status()).isEqualTo(ClaimStatus.CLOSED_REJECTED);
      assertThat(claim.externalStatus()).isNull();
    }

    @Test
    @DisplayName("非核赔中不可决策")
    void decideRejectsWrongStatus() {
      ClaimCase claim = newClaim();
      claim.accept();
      assertThatThrownBy(
              () -> claim.decide(Decision.APPROVED, "reviewer-1", null, null, BigDecimal.TEN))
          .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("双人复核与自动通过")
  class SecondReview {

    /** 造一笔 1.2 万的大额案件（触发 ≥1 万双人复核路径；核定金额 ≤ 申请金额不变量要求申请额 ≥1 万）。 */
    private static ClaimCase newLargeClaim() {
      return ClaimCase.draft(
          1002L,
          42L,
          "EG20260001",
          7L,
          "hash-abc",
          "OUTPATIENT",
          "04400001",
          "12345678",
          OUT_INSURE,
          "B20260312",
          "v3",
          List.of(
              ClaimItem.of(
                  2002L,
                  1002L,
                  "住院治疗费",
                  "",
                  ItemCategory.SERVICE,
                  BigDecimal.ONE,
                  new BigDecimal("12000.00"),
                  new BigDecimal("12000.00"))));
    }

    @Test
    @DisplayName("复核人不得为第一审核人（职责分离）")
    void reviewerMustDiffer() {
      ClaimCase claim = newLargeClaim();
      claim.accept();
      claim.allocateToPreExam("op-1");
      claim.startPreExam(false, "op-1");
      claim.completePreExam("op-1");
      claim.escalateToAdjust("op-1");
      claim.claimAdjustTask("reviewer-1");
      claim.decide(Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("12000.00"));
      assertThat(claim.requiresSecondReview()).isTrue();
      assertThatThrownBy(() -> claim.submitForSecondReview("reviewer-1"))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("非第一审核人");
      claim.submitForSecondReview("reviewer-2");
      assertThat(claim.status()).isEqualTo(ClaimStatus.REVIEWING);
      claim.completeSecondReview(true, "reviewer-2");
      assertThat(claim.status()).isEqualTo(ClaimStatus.FINISHED);
    }

    @Test
    @DisplayName("自动通过前必须回填核定金额")
    void autoApproveRequiresAmount() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.allocateToPreExam("op-1");
      claim.startPreExam(true, "SYSTEM");
      claim.completePreExam("SYSTEM");
      assertThatThrownBy(() -> claim.autoApprove("SYSTEM")).isInstanceOf(DomainException.class);
      claim.applyApprovedAmount(new BigDecimal("10.00"));
      claim.autoApprove("SYSTEM");
      assertThat(claim.status()).isEqualTo(ClaimStatus.FINISHED);
    }
  }

  @Nested
  @DisplayName("挂起正交机制")
  class HangUp {

    @Test
    @DisplayName("挂起阻断自动推进且不改状态码；恢复后回原状态")
    void hangUpDoesNotChangeStatus() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.hangUp(HangUpType.FRAUD, "risk-1");
      assertThat(claim.getHangUpStatus()).isEqualTo(1);
      assertThat(claim.hangUpTypeEnum()).isEqualTo(HangUpType.FRAUD);
      assertThat(claim.status()).isEqualTo(ClaimStatus.ACCEPTED);
      assertThat(claim.isAllocatable()).isFalse();
      claim.resume("risk-1");
      assertThat(claim.getHangUpStatus()).isEqualTo(0);
      assertThat(claim.isAllocatable()).isTrue();
    }

    @Test
    @DisplayName("重复挂起被拒")
    void doubleHangUpRejected() {
      ClaimCase claim = newClaim();
      claim.accept();
      claim.hangUp(HangUpType.EVIDENCE, "op-1");
      assertThatThrownBy(() -> claim.hangUp(HangUpType.TIMEOUT, "op-1"))
          .isInstanceOf(DomainException.class);
    }
  }

  @Test
  @DisplayName("报案不变量：至少一个明细项、被保人假名化必填、出险时间必填（逐条单违）")
  void draftInvariants() {
    List<ClaimItem> oneItem =
        List.of(
            ClaimItem.of(
                2L,
                1L,
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN));
    // 违反对：无明细
    assertThatThrownBy(
            () ->
                ClaimCase.draft(
                    1L, 42L, "P1", 1L, "h", "OUTPATIENT", "", "", OUT_INSURE, "", "v1", List.of()))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("明细");
    // 违反对：被保人假名化 ID 为空
    assertThatThrownBy(
            () ->
                ClaimCase.draft(
                    1L, 42L, "P1", 1L, "", "OUTPATIENT", "", "", OUT_INSURE, "", "v1", oneItem))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("假名化");
    // 违反对：出险时间为空
    assertThatThrownBy(
            () ->
                ClaimCase.draft(
                    1L, 42L, "P1", 1L, "h", "OUTPATIENT", "", "", null, "", "v1", oneItem))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("出险时间");
  }
}
