package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.domain.gateway.DictRenderMetricsPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.meta.OptionItem;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.EnumSourceResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 枚举选项源应用服务单测（doc/design/modules/11 + 界面设计器字典打通 v1.1 三态口径）。
 *
 * <p>判据性质：① 物化把 DEPRECATED 一并写进快照并带 state（存量写放行的凭证）；② ACTIVE 项不写 state（字节级兼容存量）； ③ 写路径解析失败
 * fail-closed（不落旧快照冒充新快照）；④ 漂移检测覆盖状态变迁；⑤ 读路径不可达回落快照（禁静默失败）； ⑥ 引用方清单按 ref.typeCode 精确匹配（SQL
 * 粗筛的内存精判），真源一次解析供 N 份快照比对。
 */
@ExtendWith(MockitoExtension.class)
class EnumSourceApplicationServiceTest {

  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private EnumSourceResolver enumSourceResolver;

  private EnumSourceApplicationService service;

  /** 本类聚焦选项源语义（非灰度），开关恒开：灰度判定由 DictBindingToggleTest 单独覆盖。 */
  private DictBindingToggle alwaysOnToggle;

  /** 记录型指标端口：不依赖 Micrometer，断言「计了什么」而不是「Micrometer 能不能注册」。 */
  private RecordingMetrics metrics;

  @BeforeEach
  void setUp() {
    alwaysOnToggle = new DictBindingToggle(true, "", () -> 0L);
    metrics = new RecordingMetrics();
    service =
        new EnumSourceApplicationService(
            metaFieldRepository, metaEntityRepository, enumSourceResolver, alwaysOnToggle, metrics);
  }

  /** 测试用指标端口（进程内计数）。 */
  private static final class RecordingMetrics implements DictRenderMetricsPort {
    private long total;
    private long fallback;

    @Override
    public void recordResolve(boolean fallbackToSnapshot) {
      total++;
      if (fallbackToSnapshot) {
        fallback++;
      }
    }

    @Override
    public long totalResolves() {
      return total;
    }

    @Override
    public long fallbackResolves() {
      return fallback;
    }
  }

  // ---------- 构造辅助 ----------

  private static MetaField dictField(Long id, String enumValues) {
    return MetaField.create(
        id,
        0L,
        100L,
        "性别",
        "gender",
        "性别",
        "ENUM",
        null,
        false,
        false,
        null,
        null,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        enumValues,
        "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"GENDER\"}}",
        null);
  }

  private static MetaField inlineField(Long id) {
    return MetaField.create(
        id,
        0L,
        100L,
        "类型",
        "kind",
        "类型",
        "ENUM",
        null,
        false,
        false,
        null,
        null,
        2,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "[{\"code\":\"A\",\"label\":\"甲\"}]",
        null,
        null);
  }

  // ---------- 物化（写路径，fail-closed） ----------

  @Test
  @DisplayName("保存物化：DEPRECATED 项带 state 进快照，ACTIVE 项不写（字节级兼容存量）")
  void materializeOnSaveWritesDeprecatedState() {
    MetaField field = dictField(7L, null);
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(
            Optional.of(
                List.of(
                    new OptionItem("M", "男"),
                    new OptionItem("F", "女"),
                    new OptionItem("U", "未知", "DEPRECATED"))));

    service.materializeOnSave(field);

    assertThat(field.getEnumValues())
        .contains("\"code\":\"M\"")
        .contains("\"code\":\"U\"")
        .contains("\"state\":\"DEPRECATED\"");
    // ACTIVE 项不得带 state 字段（存量字节形状不变）
    assertThat(field.getEnumValues()).doesNotContain("\"code\":\"M\",\"label\":\"男\",\"state\"");
    // 物化元数据回写
    assertThat(field.getEnumSource())
        .contains("\"resolvedBy\":\"save\"")
        .contains("\"resolvedCount\":3");
    verify(metaFieldRepository).update(field);
  }

  @Test
  @DisplayName("保存物化：真源不可达 fail-closed 抛错，且不落任何快照")
  void materializeOnSaveFailsClosedWhenUnresolved() {
    MetaField field = dictField(7L, "[{\"code\":\"OLD\",\"label\":\"旧\"}]");
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.materializeOnSave(field))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_ENUM_SOURCE_RESOLVE_FAILED");

    // 旧快照原样保留，update 未被调用——绝不把旧快照冒充成新发布口径
    assertThat(field.getEnumValues()).contains("OLD");
    verify(metaFieldRepository, never()).update(any());
  }

  @Test
  @DisplayName("发布物化：逐字段走查，inline 字段不动")
  void materializeForPublishSkipsInline() {
    MetaEntity entity = mock(MetaEntity.class);
    when(entity.getId()).thenReturn(100L);
    MetaField bound = dictField(7L, null);
    MetaField inline = inlineField(8L);
    when(metaFieldRepository.findByEntityId(100L)).thenReturn(List.of(bound, inline));
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男"))));

    service.materializeForPublish(entity);

    assertThat(bound.getEnumValues()).contains("\"code\":\"M\"");
    assertThat(bound.getEnumSource()).contains("\"resolvedBy\":\"publish\"");
    assertThat(inline.getEnumValues()).isEqualTo("[{\"code\":\"A\",\"label\":\"甲\"}]");
  }

  @Test
  @DisplayName("手动刷新：未绑定字段拒绝（inline 无需刷新）")
  void refreshRejectsUnboundField() {
    when(metaFieldRepository.findById(9L)).thenReturn(inlineField(9L));

    assertThatThrownBy(() -> service.refresh(9L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_ENUM_SOURCE_INVALID");
  }

  // ---------- 声明校验 ----------

  @Test
  @DisplayName("声明非法（JSON 坏 / type 越界 / 缺 typeCode）一律 400")
  void invalidDeclarationsRejected() {
    MetaField bad = dictField(7L, null);
    bad.applyEnumMaterialization(null, "{not-json");
    assertThatThrownBy(() -> service.parseDeclaration(bad.getEnumSource()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_ENUM_SOURCE_INVALID");

    MetaField wrongType = dictField(7L, null);
    wrongType.applyEnumMaterialization(null, "{\"type\":\"external\",\"ref\":{}}");
    assertThatThrownBy(() -> service.parseDeclaration(wrongType.getEnumSource()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_ENUM_SOURCE_INVALID");

    MetaField noRef = dictField(7L, null);
    noRef.applyEnumMaterialization(null, "{\"type\":\"dict\"}");
    assertThatThrownBy(() -> service.parseDeclaration(noRef.getEnumSource()))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_ENUM_SOURCE_INVALID");
  }

  // ---------- 漂移与问题项 ----------

  @Test
  @DisplayName("漂移检测：新增/移除/改标签/状态变迁四类全覆盖")
  void diffDetectsAllFourChangeKinds() {
    MetaField field =
        dictField(
            7L,
            "[{\"code\":\"M\",\"label\":\"男\"},"
                + "{\"code\":\"F\",\"label\":\"女\"},"
                + "{\"code\":\"OLD\",\"label\":\"旧\"}]");
    when(metaFieldRepository.findById(7L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptions("GENDER", null))
        .thenReturn(
            Optional.of(
                List.of(
                    new OptionItem("M", "男"),
                    new OptionItem("F", "女性"), // labelChanged
                    new OptionItem("U", "未知", "DEPRECATED")))); // added

    EnumSourceApplicationService.DriftDiff diff = service.diff(7L);

    assertThat(diff.resolved()).isTrue();
    assertThat(diff.added()).containsExactly("U");
    assertThat(diff.removed()).containsExactly("OLD");
    assertThat(diff.labelChanged()).containsExactly("F");
    assertThat(diff.stateChanged()).isEmpty(); // M 无状态变迁
    assertThat(diff.hasDrift()).isTrue();
    assertThat(diff.summary()).contains("新增 1").contains("移除 1").contains("改标签 1");
  }

  @Test
  @DisplayName("状态变迁（ACTIVE→DEPRECATED）单独构成漂移信号")
  void diffDetectsStateChangeAlone() {
    MetaField field = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.findById(7L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptions("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男", "DEPRECATED"))));

    EnumSourceApplicationService.DriftDiff diff = service.diff(7L);

    assertThat(diff.added()).isEmpty();
    assertThat(diff.removed()).isEmpty();
    assertThat(diff.labelChanged()).isEmpty();
    assertThat(diff.stateChanged()).containsExactly("M");
    assertThat(diff.hasDrift()).isTrue();
    assertThat(diff.summary()).contains("状态变迁 1");
  }

  @Test
  @DisplayName("真源不可达：diff 标记 unresolved，问题项为 ERROR")
  void unresolvedProducesErrorIssue() {
    MetaField field = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.findById(7L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptions("GENDER", null)).thenReturn(Optional.empty());

    assertThat(service.diff(7L).resolved()).isFalse();

    List<EntityValidationIssue> issues = new java.util.ArrayList<>();
    service.collectFieldIssues(field, issues);
    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).getLevel()).isEqualTo(EntityValidationIssue.LEVEL_ERROR);
    assertThat(issues.get(0).getCode()).isEqualTo("ENUM_SOURCE_UNRESOLVED");
  }

  @Test
  @DisplayName("读路径实时解析不可达：回落已物化快照（禁静默失败）")
  void liveOptionsFallsBackToSnapshot() {
    MetaField field = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.findById(7L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptions("GENDER", null)).thenReturn(Optional.empty());

    List<OptionItem> live = service.liveOptions(7L);

    assertThat(live).extracting(OptionItem::code).containsExactly("M");
  }

  @Test
  @DisplayName("inline 字段：diff 为空、liveOptions 即快照")
  void inlineFieldNeedsNoResolution() {
    MetaField field = inlineField(9L);
    when(metaFieldRepository.findById(9L)).thenReturn(field);

    assertThat(service.diff(9L).hasDrift()).isFalse();
    assertThat(service.liveOptions(9L)).extracting(OptionItem::code).containsExactly("A");
    verify(enumSourceResolver, never()).resolveDictOptions(any(), any());
  }

  @Test
  @DisplayName("漂移存在时问题项为 WARNING 且带上摘要")
  void driftProducesWarningIssue() {
    MetaField field = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(enumSourceResolver.resolveDictOptions("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男"), new OptionItem("F", "女"))));

    List<EntityValidationIssue> issues = new java.util.ArrayList<>();
    service.collectFieldIssues(field, issues);

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).getLevel()).isEqualTo(EntityValidationIssue.LEVEL_WARNING);
    assertThat(issues.get(0).getCode()).isEqualTo("ENUM_SOURCE_DRIFT");
    assertThat(issues.get(0).getMessage()).contains("新增 1");
  }

  @Test
  @DisplayName("物化元数据回写不污染建模者声明字段")
  void resolvedMetaDoesNotClobberDeclaration() {
    MetaField field = dictField(7L, null);
    when(metaFieldRepository.findById(7L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男"))));

    service.refresh(7L);

    assertThat(field.getEnumSource())
        .contains("\"type\":\"dict\"")
        .contains("\"ref\":{\"typeCode\":\"GENDER\"}")
        .contains("\"resolvedBy\":\"refresh\"");
    verify(metaFieldRepository).update(field);
  }

  @Test
  @DisplayName("字段不存在：404 语义")
  void missingFieldYieldsNotFound() {
    when(metaFieldRepository.findById(anyLong())).thenReturn(null);

    assertThatThrownBy(() -> service.refresh(404L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("META_FIELD_NOT_FOUND");
  }

  // ---------- 引用方清单（v1.1 §5.4） ----------

  @Test
  @DisplayName("引用方清单：按 ref.typeCode 精确匹配，SQL 粗筛之外的内存精判兜底")
  void listBindingsFiltersByExactTypeCode() {
    MetaField bound = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    MetaField otherType = dictField(8L, null);
    otherType.applyEnumMaterialization(
        null, "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"ORDER_STATUS\"}}");
    MetaField inline = inlineField(9L); // enum_source 为 null：粗筛漏网时精判必须排除
    when(metaFieldRepository.listDictBoundFields()).thenReturn(List.of(bound, otherType, inline));
    // 先造实体 mock 再进外层 when——when() 参数里嵌套 when() 会触发 UnfinishedStubbing
    MetaEntity entity = entityOf("员工", "bp_employee");
    when(metaEntityRepository.findById(100L)).thenReturn(entity);
    when(enumSourceResolver.resolveDictOptions("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男"), new OptionItem("F", "女"))));

    List<EnumSourceApplicationService.DictBinding> bindings = service.listBindings("GENDER");

    assertThat(bindings).hasSize(1);
    assertThat(bindings.get(0).fieldCode()).isEqualTo("gender");
    assertThat(bindings.get(0).entityName()).isEqualTo("员工");
    assertThat(bindings.get(0).snapshotCount()).isEqualTo(1);
    // 快照缺 F：真源新增一项 → 漂移待刷新
    assertThat(bindings.get(0).resolved()).isTrue();
    assertThat(bindings.get(0).drift()).isTrue();
    assertThat(bindings.get(0).driftSummary()).contains("新增 1");
    // 同一 typeCode 的真源只解析一次（N 份快照本地比对，不放大字典服务请求）
    verify(enumSourceResolver, times(1)).resolveDictOptions("GENDER", null);
  }

  @Test
  @DisplayName("引用方清单：真源不可达时逐字段标 resolved=false（禁静默当成无漂移）")
  void listBindingsMarksUnresolved() {
    MetaField bound = dictField(7L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.listDictBoundFields()).thenReturn(List.of(bound));
    when(enumSourceResolver.resolveDictOptions("GENDER", null)).thenReturn(Optional.empty());

    List<EnumSourceApplicationService.DictBinding> bindings = service.listBindings("GENDER");

    assertThat(bindings).hasSize(1);
    assertThat(bindings.get(0).resolved()).isFalse();
    assertThat(bindings.get(0).drift()).isFalse();
    assertThat(bindings.get(0).driftSummary()).isEqualTo("真源不可达");
  }

  // ---------- 灰度开关接线（v1.1 §6.5）----------

  @Test
  @DisplayName("灰度关闭：绑定视为不存在且不发起真源解析（逐字节回到 L0，非部分降级）")
  void toggleOff_treatsBindingAsAbsentWithoutResolving() {
    EnumSourceApplicationService offService =
        new EnumSourceApplicationService(
            metaFieldRepository,
            metaEntityRepository,
            enumSourceResolver,
            new DictBindingToggle(false, "", () -> 0L),
            metrics);
    MetaField field = dictField(1L, "[{\"code\":\"M\",\"label\":\"男\"}]");

    assertThat(offService.isBound(field)).isFalse();
    assertThat(offService.activeDeclaration(field)).isEmpty();
    // 关键判别：连解析都不发起——「关了还偷偷请求字典服务」会让租户误以为能力仍在线
    verifyNoInteractions(enumSourceResolver);
  }

  @Test
  @DisplayName("灰度关闭：写入口拒绝 dict 声明（META_ENUM_SOURCE_INVALID），不做「配了不生效」")
  void toggleOff_rejectsDictDeclarationOnWrite() {
    EnumSourceApplicationService offService =
        new EnumSourceApplicationService(
            metaFieldRepository,
            metaEntityRepository,
            enumSourceResolver,
            new DictBindingToggle(false, "", () -> 0L),
            metrics);
    String decl = "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"GENDER\"}}";

    assertThatThrownBy(() -> offService.validateDeclarationForWrite(decl))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo("META_ENUM_SOURCE_INVALID");
  }

  @Test
  @DisplayName("灰度为开时同一份声明可通过写入口（正对照：拒绝确由开关驱动）")
  void toggleOn_acceptsSameDeclaration() {
    String decl = "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"GENDER\"}}";

    MetaField bound = dictField(1L, "[]");
    assertThat(service.activeDeclaration(bound)).isPresent(); // 开关开 → 绑定生效
    service.validateDeclarationForWrite(decl); // 不抛即通过（与关闭态形成对照）
  }

  // ---------- 三个可观测指标（v1.1 §8）----------

  @Test
  @DisplayName("覆盖率分母含 L0 长尾（否则恒 100% 无判别力），从未物化的绑定计入漂移")
  void statsCoverageIncludesL0AndCountsNeverMaterializedAsDrift() {
    MetaField bound = dictField(1L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.listEnumFields())
        .thenReturn(List.of(bound, inlineEnumField(2L), inlineEnumField(3L)));
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.of(200L));

    EnumSourceApplicationService.DictBindingStats stats = service.dictBindingStats();

    assertThat(stats.totalEnumFields()).isEqualTo(3);
    assertThat(stats.boundEnumFields()).isEqualTo(1);
    assertThat(stats.bindingCoverage()).isEqualTo(0.3333);
    // enum_source 无 resolvedVersion ⇒ 从未物化 ⇒ 必须计入待刷新，而不是当成「无漂移」
    assertThat(stats.driftPending()).isEqualTo(1);
    assertThat(stats.unresolvedBindings()).isZero();
    // N 个绑定字段共享一份真源版本：只查一次（否则指标端点自己就是放大器的来源）
    verify(enumSourceResolver, times(1)).resolveDictVersion("GENDER");
  }

  @Test
  @DisplayName("快照版本等于真源版本 → 不计漂移（正对照，避免「一律计漂移」的恒真断言）")
  void statsNoDriftWhenVersionsMatch() {
    when(metaFieldRepository.listEnumFields()).thenReturn(List.of(boundFieldWithVersion(4L, 200L)));
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.of(200L));

    EnumSourceApplicationService.DictBindingStats stats = service.dictBindingStats();

    assertThat(stats.bindingCoverage()).isEqualTo(1.0);
    assertThat(stats.driftPending()).isZero();
  }

  @Test
  @DisplayName("真源不可达 → 单列 unresolved，不混入漂移数（两个信号不能互相污染）")
  void statsUnresolvedNotCountedAsDrift() {
    when(metaFieldRepository.listEnumFields()).thenReturn(List.of(dictField(1L, "[]")));
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.empty());

    EnumSourceApplicationService.DictBindingStats stats = service.dictBindingStats();

    assertThat(stats.unresolvedBindings()).isEqualTo(1);
    assertThat(stats.driftPending()).isZero();
  }

  @Test
  @DisplayName("解析失败回退快照 → 降级率 1.0；解析成功 → 0.0；L0 渲染不计入分母")
  void statsFallbackRateCountsOnlyRealResolveAttempts() {
    MetaField bound = dictField(1L, "[{\"code\":\"M\",\"label\":\"男\"}]");
    when(metaFieldRepository.findById(1L)).thenReturn(bound);
    when(metaFieldRepository.findById(2L)).thenReturn(inlineEnumField(2L));
    when(enumSourceResolver.resolveDictOptions("GENDER", null)).thenReturn(Optional.empty());

    // L0：未绑定 ⇒ 不是「降级」，不计入分母
    service.liveOptions(2L);
    assertThat(metrics.totalResolves()).isZero();

    // 绑定但真源不可达 ⇒ 回退快照 = 一次降级
    service.liveOptions(1L);
    assertThat(metrics.totalResolves()).isEqualTo(1);
    assertThat(metrics.fallbackResolves()).isEqualTo(1);

    when(metaFieldRepository.listEnumFields()).thenReturn(List.of(bound));
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.of(200L));
    assertThat(service.dictBindingStats().fallbackRate()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("解析成功不计降级（正对照：避免把每次解析都记成 fallback）")
  void statsSuccessfulResolveNotCountedAsFallback() {
    when(metaFieldRepository.findById(1L))
        .thenReturn(dictField(1L, "[{\"code\":\"M\",\"label\":\"男\"}]"));
    when(enumSourceResolver.resolveDictOptions("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男", "ACTIVE"))));

    service.liveOptions(1L);

    assertThat(metrics.totalResolves()).isEqualTo(1);
    assertThat(metrics.fallbackResolves()).isZero();
  }

  /** 带指定快照版本号的 dict 绑定字段（漂移判定正/负对照用）。 */
  private static MetaField boundFieldWithVersion(Long id, long resolvedVersion) {
    return MetaField.create(
        id,
        0L,
        100L,
        "性别",
        "gender_" + id,
        "性别",
        "ENUM",
        null,
        false,
        false,
        null,
        null,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "[{\"code\":\"M\",\"label\":\"男\"}]",
        "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"GENDER\"},"
            + "\"resolvedVersion\":"
            + resolvedVersion
            + "}",
        null);
  }

  /** L0（inline）ENUM 字段：覆盖率分母里的长尾。 */
  private static MetaField inlineEnumField(Long id) {
    return MetaField.create(
        id,
        0L,
        100L,
        "状态",
        "status_" + id,
        "状态",
        "ENUM",
        null,
        false,
        false,
        null,
        null,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "[{\"code\":\"A\",\"label\":\"a\"}]",
        null,
        null);
  }

  private static MetaEntity entityOf(String name, String code) {
    MetaEntity entity = mock(MetaEntity.class);
    when(entity.getName()).thenReturn(name);
    when(entity.getCode()).thenReturn(code);
    return entity;
  }
}
