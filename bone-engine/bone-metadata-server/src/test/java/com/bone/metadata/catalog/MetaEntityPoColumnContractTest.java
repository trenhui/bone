package com.bone.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.engine.runtime.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.runtime.adapter.po.MetaFieldPo;
import com.bone.metadata.sdk.domain.annotation.Column;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * catalog 写模型与引擎只读 PO 的列契约测试。
 *
 * <p>三类不合并：写模型 {@code MetaEntity}（server）/ 只读行 {@code MetaEntityPo}（engine）/ 治理模型主数据侧是三个限界上下文，见
 * ADR-0017。本测试不促成合并，只锁定一件事——PO 必须是写模型的<b>列子集</b>：catalog 加列时，要么同步更新 PO 与转换器，
 * 要么把该列登记进下方的忽略名单并写明理由。名单外的新列差集会让本测试变红。
 */
class MetaEntityPoColumnContractTest {

  /**
   * 写模型有、PO 故意没有的实体列。逐条理由：
   *
   * <ul>
   *   <li>module_id / scope / template_id——建模组织与模板追溯维度，引擎读路径经租户过滤覆盖，不参与运行时寻址；
   *   <li>row_policies——行权限策略，引擎 security 另行处理，不在元数据快照里解释；
   *   <li>auto_number——建模单据号配置，由 server 侧写路径消费，引擎读路径不需要。
   * </ul>
   */
  private static final Set<String> ENTITY_IGNORED =
      Set.of("module_id", "scope", "template_id", "row_policies", "auto_number");

  /**
   * 写模型有、PO 故意没有的字段列。逐条理由：
   *
   * <ul>
   *   <li>module_id——同实体，建模组织维度；
   *   <li>physical_column / enum_values / enum_source / validation_rules——运行时真正要用的列，PO
   *       路径成为运行时入口前必须补齐 （现行入口 {@code CatalogRuntimeEntityProvider} 直接读聚合，不受影响）；
   *   <li>data_classification / is_pii / sensitivity_level / data_steward / business_term /
   *       source_system——治理属性，引擎读路径暂不消费。
   * </ul>
   */
  private static final Set<String> FIELD_IGNORED =
      Set.of(
          "module_id",
          "physical_column",
          "enum_values",
          "enum_source",
          "validation_rules",
          "data_classification",
          "is_pii",
          "sensitivity_level",
          "data_steward",
          "business_term",
          "source_system");

  @Test
  void entityPo_shouldBeColumnSubsetOfMetaEntity() {
    Set<String> entityColumns = columnNames(MetaEntity.class);
    Set<String> poColumns = columnNames(MetaEntityPo.class);

    assertThat(poColumns).isNotEmpty();
    assertThat(entityColumns).containsAll(poColumns);

    Set<String> gap = new LinkedHashSet<>(entityColumns);
    gap.removeAll(poColumns);
    assertThat(gap).isEqualTo(ENTITY_IGNORED);
  }

  @Test
  void fieldPo_shouldBeColumnSubsetOfMetaField() {
    Set<String> fieldColumns = columnNames(MetaField.class);
    Set<String> poColumns = columnNames(MetaFieldPo.class);

    assertThat(poColumns).isNotEmpty();
    assertThat(fieldColumns).containsAll(poColumns);

    Set<String> gap = new LinkedHashSet<>(fieldColumns);
    gap.removeAll(poColumns);
    assertThat(gap).isEqualTo(FIELD_IGNORED);
  }

  @Test
  void entityPo_shouldKeepRuntimeCriticalColumns() {
    Set<String> poColumns = columnNames(MetaEntityPo.class);

    assertThat(poColumns)
        .contains("tenant_id", "name", "code", "display_name", "table_name", "status", "version");
  }

  private static Set<String> columnNames(Class<?> clazz) {
    Set<String> names = new LinkedHashSet<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        Column col = f.getAnnotation(Column.class);
        if (col != null) {
          names.add(col.name());
        }
      }
    }
    return names;
  }
}
