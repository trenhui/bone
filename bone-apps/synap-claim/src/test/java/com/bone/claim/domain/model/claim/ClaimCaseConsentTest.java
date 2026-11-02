package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ClaimCase 的 PIPL 同意留痕（工程落地版 §11.3 / DSAR 册 G10）。
 *
 * <p><b>为什么这些用例值钱</b>：同意留痕是"事后举证"类需求——写错了不会报错，只会在监管核查时发现证据链断了。 所以这里钉的不是"能存进去"，而是三条容易被人顺手改坏的语义：
 *
 * <ol>
 *   <li>证据不可就地改写（允许覆盖 = 留痕失去证明力）；
 *   <li>撤回幂等（重复撤回报错只会让柜面以为失败而重试）；
 *   <li><b>默认宽松</b>——未登记/未撤回的案件必须照常处理。少了这条正向钉子，后人把判据写成 "没登记同意就拒绝"时用例全绿，存量渠道会整片 400。
 * </ol>
 */
class ClaimCaseConsentTest {

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
                1L,
                1001L,
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"))));
  }

  @Test
  @DisplayName("正向钉子：新建案件既未登记同意、也未撤回 —— 存量渠道不受影响")
  void newClaimIsNotWithdrawnByDefault() {
    ClaimCase claim = newClaim();
    assertThat(claim.consentRecorded()).as("新案件不应凭空带上同意记录号").isFalse();
    assertThat(claim.consentWithdrawn()).as("默认必须未撤回：否则存量案件会被静默判定为不可处理").isFalse();
  }

  @Test
  @DisplayName("登记单独同意：去空格落库；同值重试幂等返回 false")
  void recordConsentIsIdempotent() {
    ClaimCase claim = newClaim();

    assertThat(claim.recordConsent("  CONSENT-2026-0001  ")).isTrue();
    assertThat(claim.getConsentRecordId()).isEqualTo("CONSENT-2026-0001").as("记录号应去首尾空格");
    assertThat(claim.consentRecorded()).isTrue();
    // 渠道重试/前端重复提交是常态，同值不算变更
    assertThat(claim.recordConsent("CONSENT-2026-0001")).isFalse();
  }

  @Test
  @DisplayName("已登记的同意记录号不可就地改写（覆盖 = 留痕失去证明力）")
  void recordConsentRefusesOverwrite() {
    ClaimCase claim = newClaim();
    claim.recordConsent("CONSENT-2026-0001");

    assertThatThrownBy(() -> claim.recordConsent("CONSENT-2026-9999"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不允许改写")
        .hasMessageContaining("数据更正工单"); // 消息必须给出正确出口，否则操作员只会找开发改库
    assertThat(claim.getConsentRecordId()).isEqualTo("CONSENT-2026-0001");
  }

  @Test
  @DisplayName("空白同意记录号拒绝（不是「先占位再补」）")
  void recordConsentRejectsBlank() {
    ClaimCase claim = newClaim();
    assertThatThrownBy(() -> claim.recordConsent("   "))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("单独同意记录号不能为空");
    assertThat(claim.consentRecorded()).isFalse();
  }

  @Test
  @DisplayName("撤回同意幂等：首次 true，再次 false（重复提交不是错误）")
  void withdrawConsentIsIdempotent() {
    ClaimCase claim = newClaim();

    assertThat(claim.withdrawConsent()).isTrue();
    assertThat(claim.consentWithdrawn()).isTrue();
    assertThat(claim.withdrawConsent()).as("重复撤回应幂等，不得抛异常").isFalse();
    assertThat(claim.consentWithdrawn()).isTrue();
  }

  @Test
  @DisplayName("撤回后不得补登记同意（PIPL 15 条：撤回后不得继续基于该同意处理）")
  void recordConsentAfterWithdrawIsRejected() {
    ClaimCase claim = newClaim();
    claim.withdrawConsent();

    assertThatThrownBy(() -> claim.recordConsent("CONSENT-2026-0002"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已撤回同意")
        .hasMessageContaining("重新同意"); // 后果 + 正确写法：让被保人重新同意后由上游签发新号
    assertThat(claim.consentRecorded()).isFalse();
  }

  @Test
  @DisplayName("撤回不改变案件状态：撤回是数据处理前提，不是状态机事件")
  void withdrawDoesNotChangeStatus() {
    ClaimCase claim = newClaim();
    claim.accept();
    var statusBefore = claim.status();

    claim.withdrawConsent();

    assertThat(claim.status())
        .as("把撤回做成状态跃迁会让状态机多出 8 条无关路径，且关单/申诉无法复用既有流转")
        .isEqualTo(statusBefore);
  }
}
