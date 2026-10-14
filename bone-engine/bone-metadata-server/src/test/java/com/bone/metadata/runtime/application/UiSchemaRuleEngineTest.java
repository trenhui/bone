package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 界面协议服务端强制求值器的判别力测试（协议 §submitRule.code 的「后端强制同一 code」承诺）。
 *
 * <p><b>判别力纪律</b>：每个用例都自问「删掉实现哪一行会红」。纯 {@code OK} 用例无法证明规则真的在跑，因此本类同时包含 <b>必红的负向用例</b>（数据不合规 → 必须产出
 * ERROR）与<b>边界放行</b>（容差内 / {@code when} 不命中 → 必须不产 ERROR）， 防止「把所有规则都判失败」这种也能过测试的退化实现。
 */
class UiSchemaRuleEngineTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final UiSchemaRuleEngine engine = new UiSchemaRuleEngine();

  private static JsonNode schema(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return m;
  }

  private List<UiSchemaRuleEngine.Violation> run(JsonNode s, Map<String, Object> data) {
    return engine.enforce(s, data, Map.of(), UiSchemaRuleEngine.Ctx.of("1", Map.of()));
  }

  // ==================== sum：报销「明细合计 = 申报金额」===================

  private static final String SUM_RULE =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "submitRules":[{"name":"明细合计一致","code":"SUB-SUM-01","function":"sum",
         "fields":["items.amount"],"operator":"eq",
         "compare":{"valueType":"field","fieldCode":"amount"},
         "verifyType":"ERROR","message":"明细合计必须等于申报金额"}]}
      """;

  @Test
  @DisplayName("sum 穿透子表路径：合计不符 → ERROR 且带规则 code（前端后端对账的抓手）")
  void sum_shouldBlock_whenChildSumMismatch() {
    List<Map<String, Object>> items = List.of(map("amount", 100.0), map("amount", 40.0));
    List<UiSchemaRuleEngine.Violation> v =
        run(schema(SUM_RULE), map("amount", 200.0, "items", items));
    assertEquals(1, v.size(), "明细合计 140 ≠ 申报 200，必须恰好一条违规");
    assertEquals("SUB-SUM-01", v.get(0).code());
    assertEquals("ERROR", v.get(0).level());
    assertTrue(v.get(0).message().contains("明细合计必须等于申报金额"));
  }

  @Test
  @DisplayName("sum 合计相符 → 无违规（防止「一律判失败」的退化实现）")
  void sum_shouldPass_whenChildSumEquals() {
    List<Map<String, Object>> items = List.of(map("amount", 100.0), map("amount", 40.0));
    assertTrue(run(schema(SUM_RULE), map("amount", 140.0, "items", items)).isEmpty());
  }

  // ==================== 容差边界（与前端 passWithTolerance 同源）===================

  private static final String CONSISTENCY_RULE =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "submitRules":[{"name":"发票与清单一致","code":"SUB-CONS-01","function":"consistency",
         "fields":["invoice_amount","items.amount"],"operator":"eq",
         "params":{"tolerance":1},"verifyType":"ERROR","message":"发票金额与清单合计不一致"}]}
      """;

  @Test
  @DisplayName("consistency 差额恰为容差 → 放行（含边界，与「差额>1 不得保存」口径一致）")
  void consistency_shouldPass_atToleranceBoundary() {
    List<Map<String, Object>> items = List.of(map("amount", 99.0));
    assertTrue(
        run(schema(CONSISTENCY_RULE), map("invoice_amount", 100.0, "items", items)).isEmpty());
  }

  @Test
  @DisplayName("consistency 差额超容差 → ERROR")
  void consistency_shouldBlock_beyondTolerance() {
    List<Map<String, Object>> items = List.of(map("amount", 98.0));
    List<UiSchemaRuleEngine.Violation> v =
        run(schema(CONSISTENCY_RULE), map("invoice_amount", 100.0, "items", items));
    assertEquals(1, v.size());
    assertEquals("SUB-CONS-01", v.get(0).code());
  }

  // ==================== compare：结束日期不得早于开始日期 ====================

  private static final String DATE_RULE =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "submitRules":[{"name":"结束日期不得早于开始日期","code":"SUB-DATE-01","function":"compare",
         "fields":["expense_end"],"operator":"gte",
         "compare":{"valueType":"field","fieldCode":"expense_start"},
         "verifyType":"ERROR","message":"结束日期不得早于开始日期"}]}
      """;

  @Test
  @DisplayName("compare 日期倒挂 → ERROR（ISO 日期字典序即时间序）")
  void compare_shouldBlock_whenEndBeforeStart() {
    List<UiSchemaRuleEngine.Violation> v =
        run(schema(DATE_RULE), map("expense_start", "2026-10-20", "expense_end", "2026-10-01"));
    assertEquals(1, v.size());
    assertEquals("SUB-DATE-01", v.get(0).code());
  }

  @Test
  @DisplayName("compare 日期正常（含同日） → 放行")
  void compare_shouldPass_whenEndNotBeforeStart() {
    assertTrue(
        run(schema(DATE_RULE), map("expense_start", "2026-10-01", "expense_end", "2026-10-01"))
            .isEmpty());
  }

  // ==================== linkage：专票必填税号 ====================

  private static final String LINKAGE_SPECIAL =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "linkageRules":[{"name":"专票必填税号","code":"LINK-TAX-01",
         "when":{"fieldCode":"invoice_type","operator":"eq","value":"SPECIAL"},
         "then":[{"fieldCode":"tax_no","visible":true,"required":true}]}]}
      """;

  @Test
  @DisplayName("linkage 命中且必填字段为空 → ERROR")
  void linkage_shouldBlock_requiredFieldMissing() {
    List<UiSchemaRuleEngine.Violation> v =
        run(schema(LINKAGE_SPECIAL), map("invoice_type", "SPECIAL", "tax_no", ""));
    assertEquals(1, v.size());
    assertTrue(v.get(0).message().contains("tax_no"));
  }

  @Test
  @DisplayName("linkage 未命中（普票）→ 不产必填违规（防规则越界误伤）")
  void linkage_shouldNotBlock_whenConditionNotMet() {
    assertTrue(run(schema(LINKAGE_SPECIAL), map("invoice_type", "NORMAL", "tax_no", "")).isEmpty());
  }

  // ==================== when 前置：拒赔完整性只在 REJECTING 跑 ====================

  private static final String COMPLETENESS_RULE =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "submitRules":[{"name":"拒赔理由完整性","code":"SUB-COMP-01","function":"completeness",
         "fields":["reject_reasons"],"operator":"gte","params":{"minReasons":3},
         "when":{"fieldCode":"status","operator":"eq","value":"REJECTING"},
         "verifyType":"ERROR","message":"拒赔必须≥3 条理由"}]}
      """;

  @Test
  @DisplayName("when 前置不命中 → 整条跳过（不误伤正常提交）")
  void completeness_shouldSkip_whenWhenNotMet() {
    assertTrue(
        run(schema(COMPLETENESS_RULE), map("status", "DRAFT", "reject_reasons", List.of("a")))
            .isEmpty());
  }

  @Test
  @DisplayName("when 命中且理由不足 → ERROR")
  void completeness_shouldBlock_whenReasonsInsufficient() {
    List<UiSchemaRuleEngine.Violation> v =
        run(
            schema(COMPLETENESS_RULE),
            map("status", "REJECTING", "reject_reasons", List.of("a", "b")));
    assertEquals(1, v.size());
    assertEquals("SUB-COMP-01", v.get(0).code());
  }

  // ==================== all 复合条件：快赔四条件（语义禁拆成 or）===================

  private static final String FASTTRACK_LINKAGE =
      """
      {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
       "linkageRules":[{"code":"LINK-FAST-01",
         "when":{"all":[{"fieldCode":"calc_amount","operator":"lte","value":500},
                        {"fieldCode":"evidences","operator":"isNotNull"},
                        {"fieldCode":"fraud_flags","operator":"isNull"},
                        {"fieldCode":"coverage_clear","operator":"eq","value":true}]},
         "then":[{"fieldCode":"fasttrack_entry","required":true}]}]}
      """;

  @Test
  @DisplayName("all 复合条件：缺一项即不命中（证明「四条件全满足」语义，未被退化成 any）")
  void allCondition_shouldNotMatch_whenOneMissing() {
    // fraud_flags 非空（应命中风险）→ 四条件不满足 → 不要求 fasttrack_entry
    assertTrue(
        run(
                schema(FASTTRACK_LINKAGE),
                map(
                    "calc_amount",
                    300.0,
                    "evidences",
                    "INV-1",
                    "fraud_flags",
                    "R-FRAUD",
                    "coverage_clear",
                    true))
            .isEmpty());
  }

  @Test
  @DisplayName("all 复合条件全满足且目标字段为空 → ERROR")
  void allCondition_shouldMatch_whenAllSatisfied() {
    List<UiSchemaRuleEngine.Violation> v =
        run(
            schema(FASTTRACK_LINKAGE),
            map(
                "calc_amount",
                300.0,
                "evidences",
                "INV-1",
                "fraud_flags",
                null,
                "coverage_clear",
                true,
                "fasttrack_entry",
                ""));
    assertEquals(1, v.size());
    assertTrue(v.get(0).message().contains("fasttrack_entry"));
  }

  // ==================== fail-closed：配置错误必须阻断 ====================

  @Test
  @DisplayName("未知函数名 → ERROR 阻断（fail-closed，配置错不静默放行）")
  void unknownFunction_shouldFailClosed() {
    String bad =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "submitRules":[{"code":"SUB-BAD-01","function":"avg","fields":["amount"],
           "operator":"eq","compare":{"valueType":"fixed","value":1},
           "verifyType":"ERROR","message":"x"}]}
        """;
    List<UiSchemaRuleEngine.Violation> v = run(schema(bad), map("amount", 1.0));
    assertEquals(1, v.size());
    assertTrue(v.get(0).message().contains("未知函数"));
  }

  @Test
  @DisplayName("sum 缺 compare 右值 → ERROR 阻断（配置错误不静默放行）")
  void sumWithoutCompare_shouldFailClosed() {
    String bad =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "submitRules":[{"code":"SUB-BAD-02","function":"sum","fields":["items.amount"],
           "operator":"eq","verifyType":"ERROR","message":"x"}]}
        """;
    List<Map<String, Object>> items = List.of(map("amount", 1.0));
    List<UiSchemaRuleEngine.Violation> v = run(schema(bad), map("items", items));
    assertEquals(1, v.size());
    assertTrue(v.get(0).message().contains("缺 compare"));
  }

  // ==================== WARNING 不阻断 ====================

  @Test
  @DisplayName("verifyType=WARNING → 产 WARNING 而非 ERROR（分级语义）")
  void warningLevel_shouldNotBeError() {
    String warn =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "submitRules":[{"code":"SUB-W-01","function":"count","fields":["items.code"],
           "operator":"gte","compare":{"valueType":"fixed","value":3},
           "verifyType":"WARNING","message":"建议至少 3 条明细"}]}
        """;
    List<Map<String, Object>> items = List.of(map("code", "A"));
    List<UiSchemaRuleEngine.Violation> v = run(schema(warn), map("items", items));
    assertEquals(1, v.size());
    assertEquals("WARNING", v.get(0).level());
  }

  // ==================== 上下文变量 $me ====================

  @Test
  @DisplayName("$me 解析为当前操作者（列表 tab「待我审批」的语义）")
  void dollarMe_shouldResolveToOperator() {
    String rule =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "linkageRules":[{"code":"LINK-ME-01",
           "when":{"fieldCode":"approver_id","operator":"eq","value":"$me"},
           "then":[{"fieldCode":"comment","required":true}]}]}
        """;
    assertEquals(
        1,
        run(schema(rule), map("approver_id", "1", "comment", "")).size(),
        "operator=1 且 approver_id=1 → 条件命中 → comment 必填");
    assertTrue(
        run(schema(rule), map("approver_id", "2", "comment", "")).isEmpty(),
        "approver_id 非本人 → 条件不命中 → 不要求 comment");
  }

  // ==================== 条件深度 fail-closed ====================

  @Test
  @DisplayName("条件嵌套超深度 → fail-closed 为不命中（防恶意/误配深层嵌套）")
  void deepCondition_shouldFailClosed() {
    // 4 层 all 嵌套 > MAX_COND_DEPTH(3)
    String deep =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "linkageRules":[{"code":"LINK-DEEP",
           "when":{"all":[{"all":[{"all":[{"all":[{"fieldCode":"a","operator":"eq","value":1}]}]}]}]},
           "then":[{"fieldCode":"b","required":true}]}]}
        """;
    assertTrue(
        run(schema(deep), map("a", 1, "b", "")).isEmpty(), "超深条件必须判为不命中，从而不施加必填（fail-closed）");
  }

  // ==================== unique 判重 ====================

  @Test
  @DisplayName("unique 子表内重复行 → ERROR（逐行组合键判重）")
  void unique_shouldBlock_whenChildRowsDuplicated() {
    String rule =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}},
         "submitRules":[{"code":"SUB-U-01","function":"unique","fields":["items.code","items.category"],
           "operator":"eq","verifyType":"ERROR","message":"明细存在重复行"}]}
        """;
    List<Map<String, Object>> dup =
        List.of(map("code", "A", "category", "X"), map("code", "A", "category", "X"));
    assertEquals(1, run(schema(rule), map("items", dup)).size());

    List<Map<String, Object>> ok =
        List.of(map("code", "A", "category", "X"), map("code", "A", "category", "Y"));
    assertTrue(run(schema(rule), map("items", ok)).isEmpty());
  }

  // ==================== 强制域收窄：ATTACHMENT/CHILD_TABLE 无物理列不做必填强制 ====================

  @Test
  @DisplayName("强制域外的字段（附件/子表，主记录无列）→ 跳过必填强制（否则规则永久死锁）")
  void requiredShortcut_shouldSkipFieldsOutsideEnforceableDomain() {
    // evidences 是 ATTACHMENT 型（无物理列），enforceable 里没有它
    List<UiSchemaRuleEngine.Violation> v =
        engine.enforce(
            schema(LINKAGE_SPECIAL),
            map("invoice_type", "SPECIAL", "tax_no", ""),
            Map.of(),
            UiSchemaRuleEngine.Ctx.of("1", Map.of()),
            Set.of("invoice_type")); // tax_no 不在域内
    assertTrue(v.isEmpty(), "域外字段不得被强制（本例若强制 tax_no 会产 1 条违规）");
  }

  @Test
  @DisplayName("强制域内的字段仍照常强制（收窄不得把真约束一起放过）")
  void requiredShortcut_shouldStillEnforceInsideDomain() {
    List<UiSchemaRuleEngine.Violation> v =
        engine.enforce(
            schema(LINKAGE_SPECIAL),
            map("invoice_type", "SPECIAL", "tax_no", ""),
            Map.of(),
            UiSchemaRuleEngine.Ctx.of("1", Map.of()),
            Set.of("invoice_type", "tax_no"));
    assertEquals(1, v.size(), "域内字段必须照常拦截");
    assertTrue(v.get(0).message().contains("tax_no"));
  }

  // ==================== 无 schema / 空 schema 不得误伤 ====================

  @Test
  @DisplayName("无 submitRules/linkageRules → 无违规（业务不得被协议阻断）")
  void emptySchema_shouldNotBlock() {
    String empty =
        """
        {"schemaVersion":1,"entityCode":"t_x","views":{"list":{},"form":{"sections":[]}}}
        """;
    assertTrue(run(schema(empty), map("anything", 1)).isEmpty());
  }
}
