package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.gateway.DictRenderMetricsPort;
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
 * L0→L1 迁移向导单测（界面设计器字典打通 v1.1 §5.3）。
 *
 * <p>判据性质：
 *
 * <ul>
 *   <li><b>分类可判别</b>：同一个 fixture 必须同时产出 matched / dictOnly / localOnly / 两类冲突， 否则「全归到某一类」的实现也能通过
 *   <li><b>冲突必须被确认</b>：未确认时抛 409 且<b>不写库</b>（不能先绑后报错）
 *   <li><b>两种策略结果不同</b>：KEEP 把本地多出项留在快照、MERGE 写进真源并从重取结果物化 —— 只断言「返回快照条数」不足以区分（两者可能恰好相等）
 *   <li><b>并入失败绝不绑定</b>：真源写入失败时字段保持 L0（fail-closed）
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EnumSourceMigrationTest {

  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private EnumSourceResolver enumSourceResolver;

  private EnumSourceApplicationService service;

  @BeforeEach
  void setUp() {
    DictRenderMetricsPort noopMetrics =
        new DictRenderMetricsPort() {
          @Override
          public void recordResolve(boolean fallbackToSnapshot) {
            // 迁移用例不断言降级率
          }

          @Override
          public long totalResolves() {
            return 0;
          }

          @Override
          public long fallbackResolves() {
            return 0;
          }
        };
    service =
        new EnumSourceApplicationService(
            metaFieldRepository,
            metaEntityRepository,
            enumSourceResolver,
            new DictBindingToggle(true, "", () -> 0L),
            noopMetrics);
  }

  // ---------- 预览 ----------

  @Test
  @DisplayName("预览：同一个 fixture 同时产出 matched/dictOnly/localOnly 与两类冲突")
  void previewClassifiesAllCategories() {
    when(metaFieldRepository.findById(1L)).thenReturn(localField(1L));
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(
            Optional.of(
                List.of(
                    new OptionItem("M", "男", "ACTIVE"), // matched
                    new OptionItem("F", "女性", "ACTIVE"), // 同码不同义
                    new OptionItem("Y", "未知", "ACTIVE")))); // 真源多出

    EnumSourceApplicationService.MigrationPlan plan = service.previewMigration(1L, "GENDER");

    assertThat(plan.matched()).containsExactly("M");
    assertThat(plan.dictOnly()).containsExactly("Y");
    assertThat(plan.localOnly()).containsExactly("X");
    assertThat(plan.labelConflicts())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.code()).isEqualTo("F");
              assertThat(c.localLabel()).isEqualTo("女");
              assertThat(c.dictLabel()).isEqualTo("女性");
            });
    // 同义不同码：本地 X=未知、真源 Y=未知
    assertThat(plan.codeConflicts())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.localCode()).isEqualTo("X");
              assertThat(c.dictCode()).isEqualTo("Y");
            });
    assertThat(plan.requiresConfirmation()).isTrue();
  }

  @Test
  @DisplayName("预览：已绑定字段拒绝迁移（避免冲掉既有快照与覆写关系）")
  void previewRejectsAlreadyBound() {
    when(metaFieldRepository.findById(2L)).thenReturn(boundField(2L));

    assertThatThrownBy(() -> service.previewMigration(2L, "GENDER"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo("META_MIGRATE_ALREADY_BOUND");
  }

  @Test
  @DisplayName("预览：目标值域不可达 → 502 MIGRATE_TARGET_UNRESOLVED（不做无依据的 diff）")
  void previewRejectsUnreachableTarget() {
    when(metaFieldRepository.findById(1L)).thenReturn(localField(1L));
    when(enumSourceResolver.resolveDictOptionsFresh("NOPE", null)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.previewMigration(1L, "NOPE"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo("META_MIGRATE_TARGET_UNRESOLVED");
  }

  // ---------- 执行 ----------

  @Test
  @DisplayName("冲突未确认 → 409 且不写库（不允许「先绑定后报错」）")
  void migrateRejectsUnconfirmedConflictsWithoutWriting() {
    when(metaFieldRepository.findById(1L)).thenReturn(localField(1L));
    // 必须真含冲突：F 在本地是「女」、在真源是「女性」⇒ 同码不同义
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(
            Optional.of(
                List.of(new OptionItem("M", "男", "ACTIVE"), new OptionItem("F", "女性", "ACTIVE"))));

    assertThatThrownBy(() -> service.migrateToDict(1L, "GENDER", "KEEP_LOCAL_OVERRIDE", false))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo("META_MIGRATE_CONFLICT_UNCONFIRMED");
    verify(metaFieldRepository, never()).update(any());
  }

  @Test
  @DisplayName("KEEP_LOCAL_OVERRIDE：本地多出项留在快照，真源不写（不擅自改别人的值域）")
  void migrateKeepLocalOverrideKeepsExtrasWithoutWritingSource() {
    MetaField field = localField(1L);
    when(metaFieldRepository.findById(1L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(
            Optional.of(
                List.of(new OptionItem("M", "男", "ACTIVE"), new OptionItem("F", "女", "ACTIVE"))));
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.of(1000L));

    EnumSourceApplicationService.MigrationResult result =
        service.migrateToDict(1L, "GENDER", "KEEP_LOCAL_OVERRIDE", true);

    verify(enumSourceResolver, never()).createDictItems(anyString(), anyList());
    // 2 个真源项 + 1 个本地追加项
    assertThat(result.snapshotCount()).isEqualTo(3);
    assertThat(result.keptLocalOverrides()).isEqualTo(1);
    assertThat(result.mergedIntoDict()).isZero();
    assertThat(field.getEnumValues()).contains("\"X\"").contains("\"M\"").contains("\"F\"");
    assertThat(field.getEnumSource()).contains("\"typeCode\":\"GENDER\"");
    verify(metaFieldRepository).update(field);
  }

  @Test
  @DisplayName("MERGE_INTO_DICT：缺失项写入真源，快照按重取结果物化（不残留本地追加）")
  void migrateMergeIntoDictWritesMissingItemsAndRefetches() {
    MetaField field = localField(1L);
    when(metaFieldRepository.findById(1L)).thenReturn(field);
    // 真源状态依赖「是否已并入 X」：与调用次数/顺序无关，避免连续 stub 把「没重取」也蒙混过去
    java.util.concurrent.atomic.AtomicBoolean merged =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenAnswer(
            inv ->
                Optional.of(
                    merged.get()
                        ? List.of(
                            new OptionItem("M", "男", "ACTIVE"), new OptionItem("X", "未知", "ACTIVE"))
                        : List.of(new OptionItem("M", "男", "ACTIVE"))));
    when(enumSourceResolver.createDictItems(eq("GENDER"), anyList()))
        .thenAnswer(
            inv -> {
              merged.set(true);
              return 1;
            });
    when(enumSourceResolver.resolveDictVersion("GENDER")).thenReturn(Optional.of(2000L));

    EnumSourceApplicationService.MigrationResult result =
        service.migrateToDict(1L, "GENDER", "MERGE_INTO_DICT", true);

    verify(enumSourceResolver).createDictItems(eq("GENDER"), anyList());
    assertThat(result.mergedIntoDict()).isEqualTo(1);
    assertThat(result.keptLocalOverrides()).isZero();
    // 关键：快照来自「并入后重取」的真源（2 项），而不是并入前的 1 项 + 本地补齐
    assertThat(result.snapshotCount()).isEqualTo(2);
    assertThat(field.getEnumValues()).contains("\"X\"");
  }

  @Test
  @DisplayName("并入字典失败 → 502 且字段保持未绑定（fail-closed，不留半吊子状态）")
  void migrateMergeFailureLeavesFieldUnbound() {
    MetaField field = localField(1L);
    when(metaFieldRepository.findById(1L)).thenReturn(field);
    when(enumSourceResolver.resolveDictOptionsFresh("GENDER", null))
        .thenReturn(Optional.of(List.of(new OptionItem("M", "男", "ACTIVE"))));
    when(enumSourceResolver.createDictItems(eq("GENDER"), anyList()))
        .thenThrow(new IllegalStateException("403 Forbidden"));

    assertThatThrownBy(() -> service.migrateToDict(1L, "GENDER", "MERGE_INTO_DICT", true))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo("META_MIGRATE_MERGE_FAILED");
    assertThat(field.getEnumSource()).isNull();
    verify(metaFieldRepository, never()).update(any());
  }

  // ---------- 构造辅助 ----------

  /** L0 字段：本地枚举 M/F/X，无 enum_source（迁移起点）。 */
  private static MetaField localField(Long id) {
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
        "[{\"code\":\"M\",\"label\":\"男\"},{\"code\":\"F\",\"label\":\"女\"},"
            + "{\"code\":\"X\",\"label\":\"未知\"}]",
        null,
        null);
  }

  /** 已绑定真源的字段（重复迁移应被拒）。 */
  private static MetaField boundField(Long id) {
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
        "[{\"code\":\"M\",\"label\":\"男\"}]",
        "{\"type\":\"dict\",\"mode\":\"snapshot\",\"ref\":{\"typeCode\":\"GENDER\"}}",
        null);
  }
}
