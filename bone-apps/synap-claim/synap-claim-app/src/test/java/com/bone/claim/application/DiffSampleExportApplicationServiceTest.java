package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.InspectionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 后督 DIFF 回流导出的表征钉子（2026-11-07 第 10 轮循环复核）。
 *
 * <p>取证结论：{@code AiEvalSampleCollectedEvent} 此前零消费方，{@code EventAiEvalSamplePortAdapter} 只打日志；而
 * DIFF 结论本身已由 {@code InspectionApplicationService.conclude()} 原子落库到 {@code
 * t_claim_inspection.result/diff_type/diff_detail}。因此本轮采用<b>零 DDL</b>方案： 直接读已落库事实源，按 DIFF
 * 投影成可回溯样本，避免新增表和迁移（L3 审批边界）， 也避免「内存聚合重启即丢」的伪闭环。
 *
 * <p>诚实边界：导出的是「后督差异索引」，不是独立盲标金标准（0122 明确二者物理分离）， 不含任何 AI 准确率/一致率数值；无样本时返回空列表而非编造 0% 或占位行。
 */
@ExtendWith(MockitoExtension.class)
class DiffSampleExportApplicationServiceTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-05T00:00:00Z");

  @Mock private InspectionRepository inspectionRepository;

  private DiffSampleExportApplicationService service;

  @BeforeEach
  void setUp() {
    service = new DiffSampleExportApplicationService(inspectionRepository);
  }

  @Test
  @DisplayName("按 DIFF 投影：归因映射三类 + 稳定幂等指纹 + 原始案件引用")
  void diffRecordsAreProjectedWithAttributionAndFingerprint() {
    // 先构造记录再桩：Mockito 不允许在 thenReturn(...) 参数里嵌套创建另一个 mock
    // （会报 UnfinishedStubbingException）。
    List<InspectionRecord> records =
        List.of(
            record("INSP-1", "ADJUST_MISMATCH", "RULE_FIX"),
            record("INSP-2", "AI_MISJUDGE", "EVAL_SET"),
            record("INSP-3", "QUOTA_ABNORMAL", "CONTROL_FIX"));
    when(inspectionRepository.findDiffConcludedInWindow(any(), any())).thenReturn(records);

    DiffSampleExportApplicationService.Export export = service.export(FROM, TO, 100);

    assertThat(export.returned()).isEqualTo(3);
    assertThat(export.truncated()).isFalse();
    assertThat(export.samples())
        .extracting(DiffSampleExportApplicationService.Sample::sampleType)
        .containsExactly("RULE_FIX", "EVAL_SET", "CONTROL_FIX");
    assertThat(export.samples())
        .allSatisfy(
            s -> {
              assertThat(s.inspectionNo()).isNotBlank();
              assertThat(s.fingerprint()).hasSize(16).matches("[0-9a-f]{16}");
              assertThat(s.claimId()).isNotNull();
            });
    // 同一抽检单 + 同一差异类型 ⇒ 指纹稳定；不同单 ⇒ 不串样
    assertThat(export.samples().get(0).fingerprint())
        .isNotEqualTo(export.samples().get(1).fingerprint());
  }

  @Test
  @DisplayName("空样本如实返回空列表，不编造数值 / 不塞占位行")
  void emptyEvidenceReturnsEmptyWithoutFabrication() {
    when(inspectionRepository.findDiffConcludedInWindow(any(), any())).thenReturn(List.of());
    DiffSampleExportApplicationService.Export export = service.export(FROM, TO, 100);
    assertThat(export.samples()).isEmpty();
    assertThat(export.returned()).isZero();
    assertThat(export.truncated()).isFalse();
  }

  @Test
  @DisplayName("仓库返回 null 时按空处理，绝不 NPE 阻断导出的读路径")
  void nullRepositoryResultIsTreatedAsEmpty() {
    when(inspectionRepository.findDiffConcludedInWindow(any(), any())).thenReturn(null);
    assertThat(service.export(FROM, TO, 100).samples()).isEmpty();
  }

  @Test
  @DisplayName("时间窗护栏：缺端点 / 逆序 / 超 31 天一律显式拒绝")
  void windowGuardRejectsInvalidRanges() {
    assertThatThrownBy(() -> service.export(null, TO, 100)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.export(FROM, null, 100)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.export(TO, FROM, 100)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.export(FROM, FROM.plus(Duration.ofDays(32)), 100))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("limit 越界显式拒绝（防拖库后门）")
  void limitGuardRejectsOutOfRange() {
    assertThatThrownBy(() -> service.export(FROM, TO, 0)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.export(FROM, TO, 501)).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("打满 SQL 上界时标记 truncated，不静默裁剪")
  void saturatedWindowIsMarkedTruncated() {
    List<InspectionRecord> full = new java.util.ArrayList<>();
    for (int i = 0; i < DiffSampleExportApplicationService.MAX_SAMPLES; i++) {
      full.add(record("INSP-" + i, "RULE_MISS", "RULE_FIX"));
    }
    when(inspectionRepository.findDiffConcludedInWindow(any(), any())).thenReturn(full);
    DiffSampleExportApplicationService.Export export = service.export(FROM, TO, 100);
    assertThat(export.returned()).isEqualTo(100);
    assertThat(export.truncated()).isTrue();
  }

  private static InspectionRecord record(
      String inspectionNo, String diffType, String ignoredSampleType) {
    InspectionRecord r = mock(InspectionRecord.class);
    lenient().when(r.getInspectionNo()).thenReturn(inspectionNo);
    lenient().when(r.getClaimId()).thenReturn(9001L);
    lenient().when(r.getDiffType()).thenReturn(diffType);
    lenient().when(r.getDiffDetail()).thenReturn("{\"field\":\"total_amount\"}");
    lenient().when(r.getActionTaken()).thenReturn("RULE_REVIEW");
    lenient().when(r.getInspectedAt()).thenReturn(FROM.plus(Duration.ofHours(1)));
    lenient().when(r.getResult()).thenReturn("DIFF");
    return r;
  }
}
