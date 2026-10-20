package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
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

  @BeforeEach
  void setUp() {
    service =
        new EnumSourceApplicationService(
            metaFieldRepository, metaEntityRepository, enumSourceResolver);
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

  private static MetaEntity entityOf(String name, String code) {
    MetaEntity entity = mock(MetaEntity.class);
    when(entity.getName()).thenReturn(name);
    when(entity.getCode()).thenReturn(code);
    return entity;
  }
}
