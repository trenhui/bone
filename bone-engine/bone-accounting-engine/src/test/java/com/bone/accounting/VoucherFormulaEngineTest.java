package com.bone.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.accounting.VoucherFormulaEngine.Ctx;
import com.bone.accounting.VoucherFormulaEngine.VoucherFormulaException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link VoucherFormulaEngine} 黄金样例（业财一体方案 §11 测试策略）：金额公式 / 条件 / 摘要渲染， 模板或映射变更后必须重跑本类。 */
class VoucherFormulaEngineTest {

  // ---------- 金额公式 ----------

  private static Ctx expenseCtx() {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("amount", new BigDecimal("170.50"));
    header.put("applicant", "张三");
    header.put("period", "2026-10");
    Map<String, List<Map<String, Object>>> childRows = new LinkedHashMap<>();
    for (Object[] row : new Object[][] {{"100", "TRAVEL"}, {"50.50", "MEAL"}, {"20", "OFFICE"}}) {
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("amount", new BigDecimal(row[0].toString()));
      r.put("category_code", row[1]);
      childRows.computeIfAbsent("items", k -> new java.util.ArrayList<>()).add(r);
    }
    Map<String, Object> currentRow = new LinkedHashMap<>();
    currentRow.put("amount", new BigDecimal("50.50"));
    currentRow.put("category_code", "MEAL");
    currentRow.put("invoice_type", "SPECIAL");
    return new Ctx(header, childRows, currentRow);
  }

  @Test
  @DisplayName("sum(items.amount) 聚合三行 = 170.50")
  void sumChildAmount() {
    assertThat(VoucherFormulaEngine.evalAmount("sum(items.amount)", expenseCtx()))
        .isEqualByComparingTo("170.50");
  }

  @Test
  @DisplayName("items.amount 取当前子行值（按行展开 scope）")
  void currentChildRowAmount() {
    assertThat(VoucherFormulaEngine.evalAmount("items.amount", expenseCtx()))
        .isEqualByComparingTo("50.50");
  }

  @Test
  @DisplayName("header.amount - sum(items.amount) = 0（头行对账公式）")
  void headerMinusSum() {
    assertThat(VoucherFormulaEngine.evalAmount("header.amount - sum(items.amount)", expenseCtx()))
        .isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("运算优先级：2 + 3 * 4 = 14")
  void precedence() {
    assertThat(VoucherFormulaEngine.evalAmount("2 + 3 * 4", expenseCtx()))
        .isEqualByComparingTo("14");
  }

  @Test
  @DisplayName("括号：(2 + 3) * 4 = 20")
  void parentheses() {
    assertThat(VoucherFormulaEngine.evalAmount("(2 + 3) * 4", expenseCtx()))
        .isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("除法保留 6 位（对齐 DECIMAL(20,6)）：10 / 3 = 3.333333")
  void divisionScale() {
    assertThat(VoucherFormulaEngine.evalAmount("10 / 3", expenseCtx()))
        .isEqualByComparingTo("3.333333");
  }

  @Test
  @DisplayName("未知字段 → fail-closed 抛异常（禁静默 0 金额）")
  void unknownField_failsClosed() {
    assertThatThrownBy(() -> VoucherFormulaEngine.evalAmount("header.nope", expenseCtx()))
        .isInstanceOf(VoucherFormulaException.class)
        .hasMessageContaining("字段不存在");
  }

  @Test
  @DisplayName("语法错误 → fail-closed 抛异常")
  void badSyntax_failsClosed() {
    assertThatThrownBy(() -> VoucherFormulaEngine.evalAmount("sum(items.amount", expenseCtx()))
        .isInstanceOf(VoucherFormulaException.class);
    assertThatThrownBy(() -> VoucherFormulaEngine.evalAmount("header.amount +", expenseCtx()))
        .isInstanceOf(VoucherFormulaException.class);
  }

  // ---------- 条件 ----------

  private static Map<String, Object> condition(Object... kv) {
    Map<String, Object> c = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      c.put(kv[i].toString(), kv[i + 1]);
    }
    return c;
  }

  @Test
  @DisplayName("子行作用域 eq：invoice_type=SPECIAL 命中")
  void conditionChildEq() {
    assertThat(
            VoucherFormulaEngine.evalCondition(
                condition(
                    "child", "items", "field", "invoice_type", "op", "eq", "value", "SPECIAL"),
                expenseCtx()))
        .isTrue();
    assertThat(
            VoucherFormulaEngine.evalCondition(
                condition("child", "items", "field", "invoice_type", "op", "eq", "value", "NONE"),
                expenseCtx()))
        .isFalse();
  }

  @Test
  @DisplayName("头表作用域 gt：amount > 5000 不命中（170.50）")
  void conditionHeaderGt() {
    assertThat(
            VoucherFormulaEngine.evalCondition(
                condition("field", "amount", "op", "gt", "value", "5000"), expenseCtx()))
        .isFalse();
  }

  @Test
  @DisplayName("in 算子：category_code ∈ [TRAVEL, MEAL] 命中")
  void conditionIn() {
    assertThat(
            VoucherFormulaEngine.evalCondition(
                condition(
                    "child",
                    "items",
                    "field",
                    "category_code",
                    "op",
                    "in",
                    "value",
                    List.of("TRAVEL", "MEAL")),
                expenseCtx()))
        .isTrue();
  }

  @Test
  @DisplayName("字段为 null → 条件不命中（条件行仅当适用才生成）")
  void conditionNullField_notMatched() {
    assertThat(
            VoucherFormulaEngine.evalCondition(
                condition("child", "items", "field", "tax_no", "op", "eq", "value", "X"),
                expenseCtx()))
        .isFalse();
  }

  @Test
  @DisplayName("不支持算子 → fail-closed 抛异常")
  void conditionUnknownOp_failsClosed() {
    assertThatThrownBy(
            () ->
                VoucherFormulaEngine.evalCondition(
                    condition("field", "amount", "op", "like", "value", "1"), expenseCtx()))
        .isInstanceOf(VoucherFormulaException.class)
        .hasMessageContaining("算子不支持");
  }

  // ---------- 摘要渲染 ----------

  @Test
  @DisplayName("摘要模板占位符替换 + 未知占位符原样保留（不阻断）")
  void renderSummary() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("applicant", "张三");
    values.put("period", "2026-10");

    assertThat(VoucherFormulaEngine.renderSummary("{applicant} {period} 差旅报销", values))
        .isEqualTo("张三 2026-10 差旅报销");
    assertThat(VoucherFormulaEngine.renderSummary("{applicant} {unknown} 报销", values))
        .isEqualTo("张三 {unknown} 报销");
  }
}
