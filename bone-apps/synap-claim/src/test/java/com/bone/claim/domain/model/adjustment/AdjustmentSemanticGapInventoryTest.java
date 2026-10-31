package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.DeductibleRule;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 理算语义覆盖清单（缺口哨兵，Go-Live 门禁 G2 的「生产引擎 = reference-impl 派生」证据面）。
 *
 * <p><b>为什么需要它</b>：双轨一致只覆盖**生产已支持的语义子集**。若不把「未支持的部分」显式登记，读者会把 「双轨全绿」误读成「与 reference-impl
 * 等价」——这就是文档型缺口最危险之处：没人说谎，但结论被放大。
 *
 * <p><b>本类的性质</b>：把 reference-impl 理算链的 10 个环节逐项标注 {@link Status}，并给出**可执行证据**。 每个 GAP
 * 都配一条「反证测试」，证明该能力当前确实不存在（而不是仅口头声明）。若某次实现补齐了某项能力， 对应反证会转红——**这是设计意图**：请同步把该项改为 SUPPORTED，并回填
 * Go-Live 门禁 G2 与 《待架构评审代码整改清单》，不要直接删断言。
 *
 * <p>参考实现：{@code doc/ai-product/synap-claim-oracle/synap-claim-settlement} （30 探针 + 19 变异 + 28 个真实
 * TPA 场景）。
 */
class AdjustmentSemanticGapInventoryTest {

  /** 覆盖状态。 */
  enum Status {
    /** 生产实现完整支持。 */
    SUPPORTED,
    /** 部分支持：口径比 reference-impl 粗，需业务确认是否可接受。 */
    PARTIAL,
    /** 未实现：reference-impl 有、生产无。 */
    GAP
  }

  /**
   * reference-impl 理算链环节（顺序即铁律顺序）。
   *
   * @param evidence 当前状态的代码依据（类/方法级），便于复核
   * @param docAnchor 文档锚（SSOT 章节或门禁编号）
   */
  record Step(String label, Status status, String evidence, String docAnchor) {}

  private static final List<Step> INVENTORY =
      List.of(
          new Step(
              "①费用明细筛选（目录/合理必要）",
              Status.PARTIAL,
              "Adjustment.apply 仅按 itemSocialFlag 做社保内外粗筛，无目录编码/合理必要性判定",
              "《理算引擎内部设计》§算法 · Go-Live G2"),
          new Step(
              "②发票级免赔",
              Status.SUPPORTED,
              "AdjustmentEngine.applyInvoiceDeductible：在比例之前、年度免赔之前，对各费用池基数按占比分摊一次绝对额扣减"
                  + "（oracle S 链 deductInvoice 口径）；经 LiabilityRule.invoiceDeductible 承载，"
                  + "fromLegacy 由 t_adj_liability.invoice_deduct 装载（V12 迁移），无配置即 none() no-op",
              "Go-Live G2"),
          new Step(
              "③第三方已付抵扣（不可获利）",
              Status.SUPPORTED,
              "FeePools.aggregate 归集时净扣 统筹+第三方已付（pooling_amount+third_party_paid），"
                  + "clamp≥0；checkSettlementConsistency 守\"已付不得超合理\"硬约束（INVOICE_SETTLEMENT_INCONSISTENT），"
                  + "与 reference-impl S17 口径一致",
              "Go-Live G2 · reference-impl S17"),
          new Step(
              "④年度免赔（作用于费用基数）",
              Status.SUPPORTED,
              "Adjustment.apply 步骤②：deductible = min(deduct, base)，先于比例",
              "§7.4 · reference-impl P16"),
          new Step("⑤赔付比例", Status.SUPPORTED, "Adjustment.apply 步骤③：afterDeduct × payRate", "§7.4"),
          new Step(
              "⑥理算级免赔（第二个免赔）",
              Status.SUPPORTED,
              "AdjustmentEngine.applyAdjustDeductible：在比例之后、控额之前，对理算金额按占比分摊一次绝对额扣减"
                  + "（oracle S 链 deductAdjust 口径）；经 LiabilityRule.adjustDeductible 承载，"
                  + "fromLegacy 由 t_adj_liability.adjust_deduct 装载（V12 迁移），无配置即 none() no-op",
              "Go-Live G2"),
          new Step(
              "⑦取整（中间 6 位 / 结果 2 位 HALF_UP）",
              Status.SUPPORTED,
              "Adjustment CALC_SCALE=6 / RESULT_SCALE=2 / HALF_UP",
              "§8.3"),
          new Step(
              "⑧多维度 min 控额",
              Status.PARTIAL,
              "Adjustment.apply 仅 liabilityLimit 单维截断；无「维度可用额」多路取交",
              "§5.3 · Go-Live G2"),
          new Step(
              "⑨逐行分位归因（分位守恒）",
              Status.SUPPORTED,
              "AdjustmentEngine.attributeToItems：池级各成分金额按费用项在池内基数占比（Money.proRata）下钻为"
                  + "逐费用项 OUTPUT 行，Σ 各行 ≡ 池（I1 守恒，oracle S27 口径）；费用项标识随"
                  + "t_adj_calculation_line.input_snapshot 落库（itemId/itemName），可供逐笔对账与申诉举证",
              "§7.4 · reference-impl S27"),
          new Step(
              "⑩跨案累计（年度免赔/责任限额）",
              Status.SUPPORTED,
              "Adjustment.apply 接收 Accumulation（必填入参）：剩余免赔 = 配置 − deductibleUsed；"
                  + "剩余责任限额 = 配置 − (claimed+frozen)；读侧 AccumulationPort 投影 LIABILITY_CAP 账户，"
                  + "写侧由 A7 结算经 QuotaApplicationService.reserveLiability 回写",
              "Go-Live G2 · reference-impl S3/S21"));

  /** 行快照里的费用项标识提取式（{@code input_snapshot} 是内联 JSON，明细表无独立列）。 */
  private static final Pattern ITEM_ID = Pattern.compile("\"itemId\":(\\d+)");

  @Test
  @DisplayName("覆盖清单固定：SUPPORTED / PARTIAL / GAP 三集合与登记一致（能力变化须显式改档）")
  void statusSetsArePinned() {
    assertThat(labelsWith(Status.SUPPORTED))
        .containsExactlyInAnyOrder(
            "②发票级免赔",
            "③第三方已付抵扣（不可获利）",
            "④年度免赔（作用于费用基数）",
            "⑤赔付比例",
            "⑥理算级免赔（第二个免赔）",
            "⑦取整（中间 6 位 / 结果 2 位 HALF_UP）",
            "⑨逐行分位归因（分位守恒）",
            "⑩跨案累计（年度免赔/责任限额）");
    assertThat(labelsWith(Status.PARTIAL))
        .containsExactlyInAnyOrder("①费用明细筛选（目录/合理必要）", "⑧多维度 min 控额");
    assertThat(labelsWith(Status.GAP)).isEmpty();
  }

  @Test
  @DisplayName("每个环节都带代码依据与文档锚：缺口不得以沉默方式存在")
  void everyStepCarriesEvidenceAndAnchor() {
    for (Step step : INVENTORY) {
      assertThat(step.evidence()).as("%s 缺代码依据", step.label()).isNotBlank();
      assertThat(step.docAnchor()).as("%s 缺文档锚", step.label()).isNotBlank();
    }
    assertThat(INVENTORY).hasSize(10);
  }

  @Test
  @DisplayName("③ 已支持（原 GAP③ 反证转正）：第三方已付（统筹/商保）从可赔基数扣除 —— 不可获利")
  void thirdPartyPaidIsOffset() {
    BigDecimal without = payoutNet("10000.00", "0"); // 净基数 10000 × 90% = 9000
    BigDecimal with = payoutNet("10000.00", "5000.00"); // 净基数 (10000−5000) × 90% = 4500

    assertThat(with)
        .as("第三方/统筹已付须从可赔基数扣除，否则赔付+已付>总费用（不可获利）")
        .isLessThan(without)
        .isEqualByComparingTo("4500.00");
  }

  @Test
  @DisplayName("⑨ 已支持（原 GAP⑨ 反证转正）：多费用行按费用项分位落 OUTPUT 行，Σ 行 ≡ 总额（分位守恒）")
  void perLineAttributionConserves() {
    Adjustment adjustment =
        Adjustment.calculate(
            9001L,
            42L,
            1001L,
            List.of(item(2001L, "100.00"), item(2002L, "200.00"), item(2003L, "300.00")),
            liability("100", "0", "0"),
            "r1",
            Accumulation.FIRST);

    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("600.00");
    assertThat(adjustment.lines()).as("三笔费用明细须各落一条 OUTPUT 行；若仍为 1 行说明逐行分位归因被回退").hasSize(3);
    // 逐行可追溯：itemId 随行快照落库（明细表无 item_id 列），缺了它这行就说不清是哪笔收费
    assertThat(adjustment.lines().stream().map(line -> itemIdOf(line.getInputSnapshot())).toList())
        .as("每行须携带费用项标识，否则无法追溯到发票上的具体收费项")
        .containsExactlyInAnyOrder(2001L, 2002L, 2003L);
    assertThat(
            adjustment.lines().stream()
                .map(line -> line.getFeePayout().stripTrailingZeros().toPlainString())
                .toList())
        .as("按各费用项基数占比分位：100/200/300 在 100%% 比例下即为 100/200/300")
        .containsExactlyInAnyOrder("100", "200", "300");
    assertThat(
            adjustment.lines().stream()
                .map(AdjustmentLine::getFeePayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .as("分位守恒（oracle S27）：Σ 各行实付 ≡ 理算总额，否则明细加不回合计（I1 破裂）")
        .isEqualByComparingTo("600.00");
  }

  @Test
  @DisplayName("⑩ 已支持（原 GAP⑩ 反证转正）：次案的免赔按首案已消抵额递减 —— 两案各 8000，次案应赔 7200 而非 0")
  void crossClaimDeductibleAccumulates() {
    // 首案：免赔 1 万未消抵完，赔 0，但已消抵 8000（这是次案的输入）
    Accumulation seeded = Accumulation.FIRST;
    Adjustment first =
        Adjustment.calculate(
            9001L,
            42L,
            1001L,
            List.of(item(2001L, "8000.00")),
            liability("90", "10000", "0"),
            "r1",
            seeded);
    assertThat(first.getTotalAmount()).isEqualByComparingTo("0.00");
    assertThat(first.getDeductibleApplied()).isEqualByComparingTo("8000.00");

    // 次案：剩余免赔只剩 2000 —— 若内核仍按配置全额扣免赔，这里必为 0.00（即原 GAP⑩ 反证）
    Adjustment second =
        Adjustment.calculate(
            9002L,
            42L,
            1002L,
            List.of(item(2002L, "8000.00")),
            liability("90", "10000", "0"),
            "r1",
            Accumulation.of(first.getDeductibleApplied(), BigDecimal.ZERO, true));

    assertThat(second.getTotalAmount())
        .as("次案：8000 − 剩余免赔 2000 = 6000，×90%% = 5400；若此处为 0 说明跨案累计被回退")
        .isEqualByComparingTo("5400.00");
    assertThat(second.getDeductibleApplied()).isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName("GAP② 已支持（原 GAP② 反证转正）：发票级免赔在比例之前扣减基数 —— 基础 1000、发票免赔 200、比例 100% ⇒ 800")
  void invoiceLevelDeductibleBeforeRatio() {
    LiabilityRule rule =
        LiabilityRule.simple(
                "L-GAP2", "v1", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, false)
            .withInvoiceDeductible(DeductibleRule.annualAbsolute(new BigDecimal("200")));
    BigDecimal payout =
        AdjustmentEngine.calculate(
                AdjustmentEngine.Request.of(
                    1L, List.of(item(2001L, "1000.00")), rule, Accumulation.FIRST))
            .totalAmount();

    assertThat(payout)
        .as("发票级免赔须先于比例作用于基数：(1000−200)×100% = 800；若此处为 1000 说明发票免赔静默失效")
        .isEqualByComparingTo("800.00");
  }

  @Test
  @DisplayName("GAP⑥ 已支持（原 GAP⑥ 反证转正）：理算级免赔在比例之后二次扣减 —— 基础 1000、比例 100%、理算免赔 200 ⇒ 800")
  void adjustmentLevelDeductibleAfterRatio() {
    LiabilityRule rule =
        LiabilityRule.simple(
                "L-GAP6", "v1", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, false)
            .withAdjustDeductible(DeductibleRule.annualAbsolute(new BigDecimal("200")));
    BigDecimal payout =
        AdjustmentEngine.calculate(
                AdjustmentEngine.Request.of(
                    1L, List.of(item(2001L, "1000.00")), rule, Accumulation.FIRST))
            .totalAmount();

    assertThat(payout)
        .as("理算级免赔须后于比例作二次绝对额扣减：1000×100% − 200 = 800；若此处为 1000 说明理算免赔静默失效")
        .isEqualByComparingTo("800.00");
  }

  @Test
  @DisplayName("GAP② + ⑥ 串联：发票免赔(前) → 比例 → 理算免赔(后) 不双重计数 —— 1000−200(发票)×100%−200(理算) = 600")
  void invoiceAndAdjustDeductibleCompose() {
    LiabilityRule rule =
        LiabilityRule.simple(
                "L-GAP26", "v1", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, false)
            .withInvoiceDeductible(DeductibleRule.annualAbsolute(new BigDecimal("200")))
            .withAdjustDeductible(DeductibleRule.annualAbsolute(new BigDecimal("200")));
    BigDecimal payout =
        AdjustmentEngine.calculate(
                AdjustmentEngine.Request.of(
                    1L, List.of(item(2001L, "1000.00")), rule, Accumulation.FIRST))
            .totalAmount();

    assertThat(payout)
        .as("发票级与理算级是两个独立免赔层，应串联而非叠加后一次扣：1000−200=800，再×100%−200=600")
        .isEqualByComparingTo("600.00");
  }

  @Test
  @DisplayName("快照键集固定：新增/删除理算参数必须显式改档（防口径静默漂移）")
  void snapshotKeysArePinned() {
    Adjustment adjustment =
        Adjustment.calculate(
            9001L,
            42L,
            1001L,
            List.of(item(2001L, "1000.00")),
            liability("80", "100", "0"),
            "r1",
            Accumulation.FIRST);
    String snapshot = adjustment.getFormulaSnapshot();

    Set<String> pinned =
        new LinkedHashSet<>(
            List.of(
                "liabilityUuid",
                "liabilityVersion",
                "payPercent",
                "deduct",
                // GAP② 发票级免赔 / GAP⑥ 理算级免赔：已落地，物化进快照便于审计对账（与 deduct 同为免赔层）
                "invoiceDeduct",
                "adjustDeduct",
                // ⑩ 跨案累计：不物化进快照，历史案件就无法自包含复算（§5.2）
                "deductibleBefore",
                "deductibleUsedBefore",
                "limit",
                "limitUsedBefore",
                "accumulationAvailable",
                "socialOnly",
                "base",
                "selfPay1Total",
                "afterDeduct",
                "ratioPayout",
                "items"));
    for (String key : pinned) {
      assertThat(snapshot).as("快照须含理算参数 %s（不可仅版本引用，§5.2）", key).contains("\"" + key + "\"");
    }
    for (String absent : List.of("thirdPartyPaid", "limitDimensions")) {
      assertThat(snapshot).as("快照不应出现 %s——若出现说明对应 GAP 已补齐，请改档", absent).doesNotContain(absent);
    }
  }

  // ===================== 夹具 =====================

  private static Set<String> labelsWith(Status status) {
    return INVENTORY.stream()
        .filter(s -> s.status() == status)
        .map(Step::label)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** 从行快照里取费用项标识（明细表无 item_id 列，该值随 {@code input_snapshot} 落库）。 */
  private static Long itemIdOf(String inputSnapshot) {
    Matcher matcher = ITEM_ID.matcher(inputSnapshot == null ? "" : inputSnapshot);
    return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
  }

  private static ClaimWithItemsProjection item(long itemId, String valid) {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(1001L);
    p.setItemId(itemId);
    p.setItemName("费用" + itemId);
    p.setItemSocialFlag("IN");
    p.setItemValidAmount(new BigDecimal(valid));
    p.setItemSelfPay1(new BigDecimal(valid));
    return p;
  }

  private static AdjLiability liability(String payPercent, String deduct, String limit) {
    return AdjLiability.of(
        1L,
        42L,
        "L-GAP",
        "v1",
        7L,
        "R01",
        "REIMBURSEMENT",
        new BigDecimal(payPercent),
        new BigDecimal(deduct),
        new BigDecimal(limit),
        1,
        0,
        "ACTIVE");
  }

  /** 责任：90%、无免赔、不封顶（隔离"第三方已付净扣"这一环，不被免赔掩盖）。 */
  private static BigDecimal payoutNet(String valid, String priorPaid) {
    ClaimWithItemsProjection p = item(2001L, valid);
    p.setItemPoolingAmount(new BigDecimal(priorPaid));
    return Adjustment.calculate(
            9001L, 42L, 1001L, List.of(p), liability("90", "0", "0"), "r1", Accumulation.FIRST)
        .getTotalAmount();
  }

  @Test
  @DisplayName("清单自身不重复：环节标签唯一（重复会让 pinned 断言漏项）")
  void inventoryLabelsAreUnique() {
    Set<String> labels = INVENTORY.stream().map(Step::label).collect(Collectors.toSet());
    assertThat(labels).hasSameSizeAs(INVENTORY);
  }
}
