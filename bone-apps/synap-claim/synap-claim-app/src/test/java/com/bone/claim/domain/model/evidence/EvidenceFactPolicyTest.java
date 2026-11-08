package com.bone.claim.domain.model.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 材料事实出域策略：哪些字段可以送去模型，哪些不行。
 *
 * <p><b>为什么这组用例值得单独存在</b>：这条边界一旦判错，方向性后果不对称——放行一个姓名/证件号，是 个人信息出域（不可撤回，模型侧已留存）；拦错一个 {@code
 * item_name}（药品名），是 AI 看不到临床核验必需的 字段（表现为"配了也判不了"，且有静默失效的特征）。所以用例必须双向：既钉住"该拦的拦住"，也钉住"不该拦的没拦"。
 *
 * <p>变异验证：把 {@link EvidenceFactPolicy#exportable} 改成恒 {@code false} ⇒ 正向用例红；改成恒 {@code true} ⇒
 * 拒绝用例红。
 */
class EvidenceFactPolicyTest {

  @Test
  @DisplayName("姓名 / 证件 / 联系方式 / 病历号一律不出域（网关正则覆盖不到姓名，这是本层的主要理由）")
  void identityFieldsAreDenied() {
    assertThat(EvidenceFactPolicy.exportable("patient_name")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("insuredName")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("id_card")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("idCardNo")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("mobile")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("bank_account")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("medical_record_no")).isFalse();
    // 裸 name 也要拦（某些解析器只给 name）
    assertThat(EvidenceFactPolicy.exportable("name")).isFalse();
  }

  @Test
  @DisplayName("正向钉子：含 name 的临床字段必须放行——按片段拦会把它一起拦掉，那才是真正的失效")
  void clinicalFieldsContainingNameAreExported() {
    assertThat(EvidenceFactPolicy.exportable("item_name")).isTrue();
    assertThat(EvidenceFactPolicy.exportable("seller_name")).isTrue();
    assertThat(EvidenceFactPolicy.exportable("diagnosis_code")).isTrue();
    assertThat(EvidenceFactPolicy.exportable("drug_code")).isTrue();
    assertThat(EvidenceFactPolicy.exportable("total_amount")).isTrue();
    // 新增材料类型带来的新码默认放行：denylist 的失败形态是可被第二层兜住的，allowlist 的失败形态是静默无材料
    assertThat(EvidenceFactPolicy.exportable("discharge_summary_type")).isTrue();
  }

  @Test
  @DisplayName("空码不放行：无从判断就不出域")
  void blankCodeIsDenied() {
    assertThat(EvidenceFactPolicy.exportable(null)).isFalse();
    assertThat(EvidenceFactPolicy.exportable("")).isFalse();
    assertThat(EvidenceFactPolicy.exportable("___")).isFalse();
  }

  @Test
  @DisplayName("码归一与大小写/分隔符无关：id_card / idCard / ID-CARD 等价")
  void codeNormalizationIsSeparatorInsensitive() {
    assertThat(EvidenceFactPolicy.normalizeCode("ID_CARD")).isEqualTo("idcard");
    assertThat(EvidenceFactPolicy.normalizeCode("idCard")).isEqualTo("idcard");
    assertThat(EvidenceFactPolicy.normalizeCode("ID-CARD")).isEqualTo("idcard");
  }

  @Test
  @DisplayName("值超长截断而不是整条丢弃：OCR 把整段文本塞进一个字段时，事实仍有价值")
  void longValueIsTruncatedNotDropped() {
    String longValue = "诊".repeat(EvidenceFactPolicy.MAX_VALUE_LENGTH + 50);

    String normalized = EvidenceFactPolicy.normalizeValue(longValue);

    assertThat(normalized).hasSize(EvidenceFactPolicy.MAX_VALUE_LENGTH);
  }

  @Test
  @DisplayName("值去首尾空白；null 值归一为空串（由调用方跳过，不在这里抛）")
  void valueIsTrimmedAndNullSafe() {
    assertThat(EvidenceFactPolicy.normalizeValue("  J06.9  ")).isEqualTo("J06.9");
    assertThat(EvidenceFactPolicy.normalizeValue(null)).isEmpty();
  }
}
