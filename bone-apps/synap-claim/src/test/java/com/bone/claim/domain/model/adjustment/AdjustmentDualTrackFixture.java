package com.bone.claim.domain.model.adjustment;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 理算「双轨一致 + 防假绿」共享夹具（G2 门禁的 CI 载体）。
 *
 * <p><b>为什么要双轨</b>：单一实现的单测只能证明「它按自己算的对」——若实现与规格同错，测试照样全绿。本 夹具从 reference-impl（{@code
 * doc/ai-product/synapClaim/reference-impl/synap-claim-settlement}，含 30 探针 + 19 变异、28 个真实 TPA
 * 场景）的理算链中，抽取**生产引擎已支持的语义子集**，另写一份**独立 oracle** 作为第二轨，逐场景比对赔款金额。
 *
 * <p><b>独立性边界（诚实声明）</b>：oracle 不复用生产的任何工具方法（独立表达式、独立钳零、独立取整路径）， 但**共用 §8.3 精度约定**（中间 6 位 / 结果 2 位 /
 * HALF_UP、比例按百分数除以 100 后量化 6 位）—— 这是规格， 不是实现细节；若绕过该约定，比对的就不是「同一口径」而是「两个口径」，比对本身失去意义。
 *
 * <p><b>生产未支持的语义</b>（第三方已付抵扣 / 发票级免赔 / 理算级免赔 / 多维 min / 跨案累计 / 逐行分位归因 / 预授权 /
 * 拒赔类型）**不在此处伪造一致性**，一律沉降到 {@link AdjustmentSemanticGapInventoryTest} 作为 机器可检的缺口哨兵。
 *
 * <p><b>变异体（mutants）</b>：{@link #MUTANTS} 内每个条目都是一份「被改坏的 oracle」（顺序颠倒、漏钳零、
 * 漏封顶、舍入方向错、漏社保过滤、精度坍塌…）。正确实现必须能分辨出它们——这是「测试有没有判别力」的 唯一证明；只断言「正确 oracle 全绿」无法排除假绿。
 */
final class AdjustmentDualTrackFixture {

  static final long TENANT = 42L;
  static final long CLAIM = 1001L;

  /** 百分数 → 小数（§8.3）：{@code 90} → {@code 0.900000}。 */
  private static final BigDecimal CENT = new BigDecimal("0.01");

  private static final int CALC_SCALE = 6;
  private static final int RESULT_SCALE = 2;

  private AdjustmentDualTrackFixture() {}

  // ===================== 场景模型 =====================

  /**
   * 一条费用明细（仅取理算必需的字段）。
   *
   * @param headOnly 纯头行（`item_id` 为空，LEFT JOIN 的赔案主行）——参与断言「必须被跳过」， 避免把赔案头金额重复计入费用基数
   */
  record Row(
      String name, String socialFlag, BigDecimal valid, BigDecimal selfPay1, boolean headOnly) {}

  /**
   * 一个理算场景。
   *
   * @param label 场景标识（对齐 reference-impl 的 P 与 S 编号时直接沿用）
   * @param note 人类可读的算式说明，失败时便于定位是「算式错」还是「实现错」
   * @param rows 费用明细
   * @param payPercent 赔付比例（百分数）
   * @param deduct 年度免赔额
   * @param limit 责任额度上限（0 = 不封顶，见 reference-impl S25）
   * @param socialOnly 是否仅赔社保范围内
   */
  record Scenario(
      String label,
      String note,
      List<Row> rows,
      BigDecimal payPercent,
      BigDecimal deduct,
      BigDecimal limit,
      boolean socialOnly) {}

  /** 第二轨：给定场景算出「应当赔付多少」。 */
  interface Oracle {
    String name();

    BigDecimal payout(Scenario s);
  }

  // ===================== 场景集 =====================

  static Row row(String name, String flag, String valid) {
    return new Row(name, flag, new BigDecimal(valid), new BigDecimal(valid), false);
  }

  static Row row(String name, String flag, String valid, String selfPay1) {
    return new Row(name, flag, new BigDecimal(valid), new BigDecimal(selfPay1), false);
  }

  /** 纯头行：`item_id` 为空，金额再大也必须被跳过。 */
  static Row headRow(String name) {
    return new Row(name, "", new BigDecimal("999999.99"), BigDecimal.ZERO, true);
  }

  static Scenario scenario(
      String label,
      String note,
      List<Row> rows,
      String payPercent,
      String deduct,
      String limit,
      boolean socialOnly) {
    return new Scenario(
        label,
        note,
        rows,
        new BigDecimal(payPercent),
        new BigDecimal(deduct),
        new BigDecimal(limit),
        socialOnly);
  }

  /**
   * 双轨比对场景集。
   *
   * <p>覆盖：主链路 / **免赔先于比例作用于费用基数（P16 铁律，错序系统性少赔 25%）** / 免赔大于费用（负值钳零）/ 封顶截断 / 0 额度=不封顶 /
   * 社保内外过滤（含小写标记的健壮性）/ 未触顶 / 小数精度（HALF_UP 两位）/ 大额浮点坍塌 / 比例三位以上小数 / 免赔三位小数 / 全额赔付。
   */
  static List<Scenario> scenarios() {
    return List.of(
        // —— 真实 TPA 锚定（reference-impl S1/S3/S6/S17 的可支持投影）——
        scenario(
            "P16-铁律",
            "(20000-10000)×80%=8000（若先比例后免赔得 6000，少赔 25%）",
            List.of(row("住院费", "IN", "20000.00")),
            "80",
            "10000",
            "0",
            true),
        scenario(
            "S1-门诊快赔",
            "(1000-100)×80%=720",
            List.of(row("门诊费", "IN", "1000.00")),
            "80",
            "100",
            "0",
            true),
        scenario(
            "S3-年度免赔耗尽",
            "8000 费用 − 1 万免赔 → 0（ZERO_DEDUCT）",
            List.of(row("住院费", "IN", "8000.00")),
            "90",
            "10000",
            "0",
            true),
        scenario(
            "S6-单次限额截断",
            "200×90%=180，限额 100 → 100",
            List.of(row("检查费", "IN", "200.00")),
            "90",
            "0",
            "100",
            true),
        scenario(
            "S17-社保内口径",
            "(10000-5000)×80%=4000（社保外费用不计入基准）",
            List.of(row("住院费", "IN", "10000.00")),
            "80",
            "5000",
            "0",
            true),
        // —— 语义边界与护栏 ——
        scenario(
            "P3-免赔超费用钳零",
            "费用 30 − 免赔 300 → 基准钳零后赔付 0，绝不为负",
            List.of(row("诊查费", "IN", "30.00")),
            "90",
            "300",
            "0",
            true),
        scenario(
            "P4-未触顶",
            "200−50=150，×90%=135，额度 1 万不截断",
            List.of(row("诊查费", "IN", "200.00")),
            "90",
            "50",
            "10000",
            true),
        scenario(
            "S25-零额度不封顶",
            "额度 0 表示不限制，1000×80%=800（不得全额拒付）",
            List.of(row("住院费", "IN", "1000.00")),
            "80",
            "0",
            "0",
            true),
        scenario(
            "R-DIR-01-社保外剔除",
            "社保内 100 计入、社保外 300 剔除 → 90",
            List.of(row("社保内", "IN", "100.00"), row("社保外", "OUT", "300.00")),
            "90",
            "0",
            "0",
            true),
        scenario(
            "R-DIR-01-不限定社保",
            "socialOnly=false 时社保外也计入：(100+300)×90%=360",
            List.of(row("社保内", "IN", "100.00"), row("社保外", "OUT", "300.00")),
            "90",
            "0",
            "0",
            false),
        scenario(
            "R-DIR-01-小写标记",
            "标记小写 in 仍属社保内（大小写不敏感）→ 90",
            List.of(row("社保内", "in", "100.00")),
            "90",
            "0",
            "0",
            true),
        scenario(
            "无明细纯头行",
            "头行（item_id 空）即便带 999999.99 也不得计入：(500)×80%=400",
            List.of(headRow("赔案头行"), row("住院费", "IN", "500.00")),
            "80",
            "0",
            "0",
            true),
        // —— 精度约定（§8.3）——
        scenario(
            "8.3-两位舍入",
            "33.33×90%=29.997 → HALF_UP 30.00",
            List.of(row("诊查费", "IN", "33.33")),
            "90",
            "0",
            "0",
            true),
        scenario(
            "8.3-舍入向下",
            "33.323×90%=29.9907 → 29.99（不得进位成 30.00）",
            List.of(row("诊查费", "IN", "33.323")),
            "90",
            "0",
            "0",
            true),
        scenario(
            "8.3-大额精度",
            "12345678.91×90%=11111111.019 → 11111111.02（double/float 中转会坍塌）",
            List.of(row("大额住院费", "IN", "12345678.91")),
            "90",
            "0",
            "0",
            true),
        scenario(
            "8.3-比例六位",
            "33.333333% 量化 6 位=0.333333：(200-50)×0.333333=49.99995 → 50.00",
            List.of(row("诊查费", "IN", "200.00")),
            "33.333333",
            "50",
            "0",
            true),
        scenario(
            "8.3-免赔三位",
            "免赔 0.009：(1000−0.009)×100%=999.991 → 999.99",
            List.of(row("住院费", "IN", "1000.00")),
            "100",
            "0.009",
            "0",
            true),
        scenario(
            "全额赔付",
            "比例 100%、无免赔、不封顶 → 500.00",
            List.of(row("住院费", "IN", "500.00")),
            "100",
            "0",
            "0",
            true),
        // —— 门槛与封顶的组合 ——
        scenario(
            "免赔后触顶",
            "500−100=400，×90%=360，额度 100 → 100",
            List.of(row("住院费", "IN", "500.00")),
            "90",
            "100",
            "100",
            true),
        scenario(
            "封顶等于实付",
            "360 恰好等于额度，不截断",
            List.of(row("住院费", "IN", "400.00")),
            "90",
            "0",
            "360",
            true));
  }

  // ===================== 第二轨：参考 oracle =====================

  /**
   * 正确 oracle（reference-impl 理算链在生产已支持子集上的投影）。
   *
   * <p>链序（**顺序不可颠倒**，reference-impl 铁律）： ①社保范围内筛选 → ②年度免赔（作用于费用基数）→ ③赔付比例 → ④单维限额取 min → ⑤结果两位取整。
   */
  static final Oracle REFERENCE =
      new Oracle() {
        @Override
        public String name() {
          return "REFERENCE";
        }

        @Override
        public BigDecimal payout(Scenario s) {
          BigDecimal base = BigDecimal.ZERO;
          for (Row r : s.rows()) {
            if (r.headOnly()) {
              continue;
            }
            if (!s.socialOnly() || "IN".equalsIgnoreCase(r.socialFlag())) {
              base = base.add(r.valid());
            }
          }
          BigDecimal deductible = s.deduct().min(base);
          BigDecimal net = base.subtract(deductible);
          if (net.signum() < 0) {
            net = BigDecimal.ZERO;
          }
          BigDecimal rate =
              s.payPercent().multiply(CENT).setScale(CALC_SCALE, RoundingMode.HALF_UP);
          BigDecimal gross = net.multiply(rate).setScale(CALC_SCALE, RoundingMode.HALF_UP);
          BigDecimal cap = s.limit();
          BigDecimal payable = cap.signum() > 0 ? gross.min(cap) : gross;
          return payable.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
        }
      };

  // ===================== 第三轨：变异体（判别力证据） =====================

  /** 共用的小工具：只做「基数 + 比例 + 封顶」，供各变异体改写某一步。 */
  private static BigDecimal base(Scenario s, boolean respectSocialScope) {
    BigDecimal sum = BigDecimal.ZERO;
    for (Row r : s.rows()) {
      if (r.headOnly()) {
        continue;
      }
      if (!respectSocialScope || !s.socialOnly() || "IN".equalsIgnoreCase(r.socialFlag())) {
        sum = sum.add(r.valid());
      }
    }
    return sum;
  }

  private static BigDecimal rate6(Scenario s) {
    return s.payPercent().multiply(CENT).setScale(CALC_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal cap(Scenario s, BigDecimal amount) {
    return s.limit().signum() > 0 ? amount.min(s.limit()) : amount;
  }

  /**
   * 19 类变异 → 正确实现必须全部识别（逐条对应 reference-impl 的变异对照表，并按生产语义补齐边界项）。
   *
   * <p>任一变异「零场景可辨」即说明该缺陷能悄悄溜过整套测试 —— 测试形同虚设。
   */
  static Map<String, Oracle> mutants() {
    Map<String, Oracle> m = new LinkedHashMap<>();

    // M1 顺序颠倒：先比例后免赔（经典系统性少赔 25%）
    m.put(
        "M1_比例先于免赔",
        mutant(
            "M1_比例先于免赔",
            s -> {
              BigDecimal gross =
                  base(s, true).multiply(rate6(s)).setScale(CALC_SCALE, RoundingMode.HALF_UP);
              BigDecimal net = gross.subtract(s.deduct()).max(BigDecimal.ZERO);
              return cap(s, net).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M2 顺序颠倒：先限额后免赔
    m.put(
        "M2_限额先于免赔",
        mutant(
            "M2_限额先于免赔",
            s -> {
              BigDecimal capped =
                  s.limit().signum() > 0 ? base(s, true).min(s.limit()) : base(s, true);
              BigDecimal net = capped.subtract(s.deduct()).max(BigDecimal.ZERO);
              return net.multiply(rate6(s)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M3 重复扣减免赔
    m.put(
        "M3_免赔扣两次",
        mutant(
            "M3_免赔扣两次",
            s -> {
              BigDecimal b = base(s, true);
              BigDecimal net = b.subtract(s.deduct()).subtract(s.deduct()).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M4 漏钳零：基准减免赔后出现负数并一路传播
    m.put(
        "M4_漏负值钳零",
        mutant(
            "M4_漏负值钳零",
            s -> {
              BigDecimal net = base(s, true).subtract(s.deduct());
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M5 结果截断（向下）
    m.put(
        "M5_结果截断",
        mutant(
            "M5_结果截断",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              BigDecimal gross = net.multiply(rate6(s));
              return cap(s, gross).setScale(RESULT_SCALE, RoundingMode.DOWN);
            }));

    // M6 结果进位（向上）
    m.put(
        "M6_结果进位",
        mutant(
            "M6_结果进位",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              BigDecimal gross = net.multiply(rate6(s));
              return cap(s, gross).setScale(RESULT_SCALE, RoundingMode.UP);
            }));

    // M7 忽略责任额度
    m.put(
        "M7_忽略限额",
        mutant(
            "M7_忽略限额",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              return net.multiply(rate6(s)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M8 把「额度 0」误读为「额度为 0」（语义反转：0 应为不封顶）
    m.put(
        "M8_零额度误会",
        mutant(
            "M8_零额度误会",
            s -> {
              if (s.limit().signum() == 0) {
                return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
              }
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M9 漏社保过滤
    m.put(
        "M9_漏社保过滤",
        mutant(
            "M9_漏社保过滤",
            s -> {
              BigDecimal net =
                  base(s, false).subtract(s.deduct().min(base(s, false))).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M10 社保过滤方向反了（只算社保外）
    m.put(
        "M10_社保过滤反向",
        mutant(
            "M10_社保过滤反向",
            s -> {
              BigDecimal sum = BigDecimal.ZERO;
              for (Row r : s.rows()) {
                if (r.headOnly()) {
                  continue;
                }
                if (!s.socialOnly() || !"IN".equalsIgnoreCase(r.socialFlag())) {
                  sum = sum.add(r.valid());
                }
              }
              BigDecimal net = sum.subtract(s.deduct().min(sum)).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M11 结果只到 6 位（漏最终取整）
    m.put(
        "M11_结果未取两位",
        mutant(
            "M11_结果未取两位",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(CALC_SCALE, RoundingMode.HALF_UP);
            }));

    // M12 比例未除以 100（90 当 9000%）
    m.put(
        "M12_比例未折百",
        mutant(
            "M12_比例未折百",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              return cap(s, net.multiply(s.payPercent()))
                  .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M13 比例除以 10000（多折一次）
    m.put(
        "M13_比例多折百",
        mutant(
            "M13_比例多折百",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              BigDecimal r =
                  s.payPercent()
                      .multiply(new BigDecimal("0.0001"))
                      .setScale(CALC_SCALE, RoundingMode.HALF_UP);
              return cap(s, net.multiply(r)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M14 浮点中转（大额精度坍塌）
    m.put(
        "M14_浮点中转",
        mutant(
            "M14_浮点中转",
            s -> {
              float b = base(s, true).floatValue();
              float d = s.deduct().min(base(s, true)).floatValue();
              float r = s.payPercent().floatValue() / 100f;
              BigDecimal out = BigDecimal.valueOf((b - d) * r);
              return cap(s, out).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M15 漏扣免赔
    m.put(
        "M15_漏扣免赔",
        mutant(
            "M15_漏扣免赔",
            s ->
                cap(s, base(s, true).multiply(rate6(s)))
                    .setScale(RESULT_SCALE, RoundingMode.HALF_UP)));

    // M16 额度先被免赔吃掉
    m.put(
        "M16_额度被免赔吃掉",
        mutant(
            "M16_额度被免赔吃掉",
            s -> {
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              BigDecimal gross = net.multiply(rate6(s));
              BigDecimal reduced = s.limit().signum() > 0 ? s.limit().subtract(s.deduct()) : gross;
              return reduced.signum() > 0
                  ? gross.min(reduced)
                  : BigDecimal.ZERO.setScale(RESULT_SCALE);
            }));

    // M17 免赔被截到两位（免赔含三位小数时失真）
    m.put(
        "M17_免赔被截断",
        mutant(
            "M17_免赔被截断",
            s -> {
              BigDecimal d =
                  s.deduct().setScale(RESULT_SCALE, RoundingMode.DOWN).min(base(s, true));
              BigDecimal net = base(s, true).subtract(d).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M18 社保标记大小写敏感（"in" 被当作社保外）
    m.put(
        "M18_标记大小写敏感",
        mutant(
            "M18_标记大小写敏感",
            s -> {
              BigDecimal sum = BigDecimal.ZERO;
              for (Row r : s.rows()) {
                if (r.headOnly()) {
                  continue;
                }
                if (!s.socialOnly() || "IN".equals(r.socialFlag())) {
                  sum = sum.add(r.valid());
                }
              }
              BigDecimal net = sum.subtract(s.deduct().min(sum)).max(BigDecimal.ZERO);
              return cap(s, net.multiply(rate6(s))).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    // M19 比例只量化两位（0.333333 被砍成 0.33）
    m.put(
        "M19_比例只留两位",
        mutant(
            "M19_比例只留两位",
            s -> {
              BigDecimal r =
                  s.payPercent().multiply(CENT).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
              BigDecimal net =
                  base(s, true).subtract(s.deduct().min(base(s, true))).max(BigDecimal.ZERO);
              return cap(s, net.multiply(r)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
            }));

    return m;
  }

  private static Oracle mutant(
      String name, java.util.function.Function<Scenario, BigDecimal> body) {
    return new Oracle() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public BigDecimal payout(Scenario s) {
        return body.apply(s);
      }
    };
  }

  // ===================== 被测的生产引擎 =====================

  /** 驱动生产 `Adjustment` 聚合算出赔款（唯一入口：`Adjustment.calculate`）。 */
  static BigDecimal production(Scenario s) {
    List<ClaimWithItemsProjection> rows = new ArrayList<>();
    long itemId = 2001L;
    for (Row r : s.rows()) {
      rows.add(projection(itemId++, r));
    }
    AdjLiability liability =
        AdjLiability.of(
            1L,
            TENANT,
            "L-DUAL",
            "v1",
            7L,
            "R01",
            "REIMBURSEMENT",
            s.payPercent(),
            s.deduct(),
            s.limit(),
            s.socialOnly() ? 1 : 0,
            0,
            "ACTIVE");
    return Adjustment.calculate(9001L, TENANT, CLAIM, rows, liability, "r1", Accumulation.FIRST)
        .getTotalAmount();
  }

  /**
   * 造明细投影。
   *
   * <p>{@code itemId} 必须非空——生产实现按「无明细的纯头行跳过」处理（LEFT JOIN 语义），置空会让整行被跳过， 比对就退化成「0 对 0」的假绿。
   */
  private static ClaimWithItemsProjection projection(long itemId, Row r) {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(CLAIM);
    p.setClaimNo("CL001");
    p.setItemId(r.headOnly() ? null : itemId);
    p.setItemName(r.name());
    p.setItemSocialFlag(r.socialFlag());
    p.setItemValidAmount(r.valid());
    p.setItemSelfPay1(r.selfPay1());
    p.setItemInvalidAmount(BigDecimal.ZERO);
    return p;
  }
}
