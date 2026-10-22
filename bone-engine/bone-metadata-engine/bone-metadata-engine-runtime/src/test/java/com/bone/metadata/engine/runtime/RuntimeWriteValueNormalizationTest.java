package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 多值字段（ATTACHMENT / 多选 ENUM / JSON 配置）写库必须归一化为 JSON 文本。
 *
 * <p><b>背景（2026-10-21 全模块联调实测）</b>：理赔实体「材料证据」在 UI 里点「+ 添加附件」后， 前端 ATTACHMENT 控件产出的值是 {@code
 * [{name:"xxx.pdf"}]}（对象数组）。它原样进 {@code MapSqlParameterSource} 后由 MySQL 驱动走 {@code setObject} 做
 * <b>Java 原生序列化</b>，写入 {@code text} 列时报 {@code Incorrect string value: '\xAC\xED\x00\x05sr...' for
 * column 'evidences'}， 被 catch-all 兜成 <b>500 COMMON_INTERNAL_ERROR</b>——纯数据形态问题被当成服务端故障。
 *
 * <p>判据是「值形态」而非「不抛异常」：多值必须变成合法 JSON 文本，标量必须原样透传（不得被污染成 JSON）。
 */
class RuntimeWriteValueNormalizationTest {

  @Test
  @DisplayName("ATTACHMENT 对象数组 → JSON 文本（不得落 Java 原生序列化字节）")
  void attachmentListBecomesJson() {
    Object out = JdbcRuntimeRecordService.toDbBindable(List.of(Map.of("name", "e2e-evidence.pdf")));

    assertTrue(out instanceof String, "多值必须归一化为文本，实际类型=" + out.getClass());
    String json = (String) out;
    assertTrue(json.startsWith("[") && json.contains("e2e-evidence.pdf"), "应为 JSON 数组文本: " + json);
    assertTrue(!json.contains("\u00AC\u00ED"), "绝不能是 Java 序列化魔数开头: " + json);
  }

  @Test
  @DisplayName("嵌套 Map / 多选数组 → JSON 文本")
  void nestedStructuresBecomeJson() {
    // 用 LinkedHashMap 而非 Map.of：Map.of 的迭代顺序由 JVM 随机 SALT 决定，
    // 断言精确 JSON 文本会随机失败（b=a 与 a=b 顺序不固定）——这是测试自身的 flaky，不是实现问题。
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("a", List.of(1, 2));
    nested.put("b", true);
    Object m = JdbcRuntimeRecordService.toDbBindable(nested);
    assertEquals("{\"a\":[1,2],\"b\":true}", String.valueOf(m));

    Object arr = JdbcRuntimeRecordService.toDbBindable(List.of("OUTPATIENT", "DENTAL"));
    assertEquals("[\"OUTPATIENT\",\"DENTAL\"]", String.valueOf(arr));
  }

  @Test
  @DisplayName("标量原样透传：String/Number/Boolean/日期 不被 JSON 化")
  void scalarsPassThrough() {
    assertSame("POL-1", JdbcRuntimeRecordService.toDbBindable("POL-1"));
    assertSame(BigDecimal.TEN, JdbcRuntimeRecordService.toDbBindable(BigDecimal.TEN));
    assertSame(Boolean.TRUE, JdbcRuntimeRecordService.toDbBindable(Boolean.TRUE));
    LocalDate d = LocalDate.of(2026, 10, 21);
    assertSame(d, JdbcRuntimeRecordService.toDbBindable(d), "日期必须原样透传（同一实例）");
    assertEquals(null, JdbcRuntimeRecordService.toDbBindable(null));
  }
}
