package com.bone.masterdata.domain.model.goldenrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link GoldenRecord} 构造期的逐字段 lineage 不变量（内核契约测试）。
 *
 * <p><b>为什么单独成类</b>：这条不变量属于内核，不属于采购装配层。此前它被写在 {@code SupplierMergeAssemblyTest}
 * 里、标题又声称「装配层不可能观察到不完整记录」， 但用例根本没有经过装配层 —— 属于位置错配的假绿（独立审查 W-02）。装配层不会产出缺 lineage 的记录，因为合并链路里唯一能构造
 * {@link GoldenRecord} 的入口就是本类所测的构造器；把断言放回内核位置，才既真实又不重复。
 *
 * <p><b>防退化意义</b>：只要有人删掉 {@code of(...)} 里的逐字段校验循环，本类立刻转红 —— 这正是「部分可溯源的黄金记录」绝不允许进入分发链路的最后一道闸。
 */
@DisplayName("Golden Record 内核：缺 lineage 即整笔拒绝")
class GoldenRecordLineageInvariantTest {

  private static final long TENANT = 7L;
  private static final Instant AT = Instant.parse("2026-11-16T02:00:00Z");

  @Test
  @DisplayName("有值字段但没有对应 lineage：构造即拒，不产出部分可溯源记录")
  void rejectsValuedFieldWithoutLineage() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("supplier_name", "ACME");

    assertThatThrownBy(() -> GoldenRecord.of(TENANT, "PARTY-1", 1L, values, Map.of(), AT))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("lineage");
  }

  @Test
  @DisplayName("lineage 的 fieldCode 与字段键不一致：视为缺失，同样拒绝")
  void rejectsLineageBoundToAnotherFieldCode() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("supplier_name", "ACME");
    Map<String, FieldProvenance> mismatched = new LinkedHashMap<>();
    mismatched.put("supplier_name", FieldProvenance.of("tax_id", "R1", "SRM", "RULE-1", AT, null));

    assertThatThrownBy(() -> GoldenRecord.of(TENANT, "PARTY-1", 1L, values, mismatched, AT))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("缺少 lineage");
  }

  @Test
  @DisplayName("逐字段 lineage 完整：构造通过，且字段与血缘一一对应")
  void acceptsFullyTraceableRecord() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("supplier_name", "ACME");
    Map<String, FieldProvenance> lineage = new LinkedHashMap<>();
    lineage.put(
        "supplier_name", FieldProvenance.of("supplier_name", "R1", "SRM", "RULE-1", AT, null));

    GoldenRecord record = GoldenRecord.of(TENANT, "PARTY-1", 1L, values, lineage, AT);

    assertThat(record.getValues()).containsEntry("supplier_name", "ACME");
    assertThat(record.getProvenance()).containsOnlyKeys("supplier_name");
    assertThat(record.getProvenance().get("supplier_name").getSourceRecordId()).isEqualTo("R1");
  }
}
