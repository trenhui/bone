package com.bone.claim.domain.model.adjustment;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 理算聚合根（A6，多租户）。
 *
 * <p><b>AI 不碰钱</b>（P1 原则）：本类是赔付金额的**唯一**计算入口，全部为确定性 {@link BigDecimal} 运算——中间 6 位、结果 2
 * 位、HALF_UP（§8.3 精度约定），禁止 double 中转。
 *
 * <p>理算链（§7.4，顺序不可颠倒）：
 *
 * <ol>
 *   <li>计算基准：Σ 合理费用（{@code validAmount}），{@code socialOnly} 时仅累计社保内；
 *   <li>免赔扣减：<b>剩余</b>年度免赔 = 配置免赔 − 跨案已消抵（R-DED-03，见 {@link Accumulation}）；
 *   <li>赔付比例：{@code payRate}（百分数存储，此处转小数，上限 100% 钳制，防配置超赔）；
 *   <li>限额截断：与<b>剩余</b>责任额度取 {@code min}（§5.3 多维度控额语义，任一维度耗尽即截断）。
 * </ol>
 *
 * <p><b>跨案累计（⑩）不是可选优化</b>：免赔与限额都是<b>保单年度内</b>的口径。若按案各用满额， 同保单第 2 案起必然一侧系统性少赔（免赔重扣）、一侧系统性超赔（限额重置）。 故
 * {@link Accumulation} 是理算的<b>必填入参</b>——没有"默认首案"的重载，就是为了杜绝调用方 无意间按"已用 0"算（那正是本项目铁律点名的「配了不生效」）。
 *
 * <p><b>快照不可仅版本引用</b>（§5.2）：理算把当期责任参数**物化**进 {@code formulaSnapshot}，
 * 而非只存版本引用——源配置后续被改时，本记录仍可自包含复算（金融理算「同输入同输出」是合规硬要求）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_adj_calculation")
public class Adjustment extends TenantAggregateRoot<Long> {

  /** 中间计算精度（§8.3 CALC_SCALE=6）。 */
  private static final int CALC_SCALE = 6;

  /** 结果精度（§8.3 RESULT_SCALE=2）。 */
  private static final int RESULT_SCALE = 2;

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  /** 理算结果状态：FROZEN 已冻结 / CONFIRMED 已确认 / UNFROZEN 已解冻 / ROLLBACKED 已回滚（§5.3）。 */
  public enum ResultStatus {
    FROZEN,
    CONFIRMED,
    UNFROZEN,
    ROLLBACKED
  }

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "liability_uuid", nullable = false)
  private String liabilityUuid;

  @Column(name = "liability_version", nullable = false)
  private String liabilityVersion;

  @Column(name = "rule_version")
  private String ruleVersion;

  /** 公式快照（物化的责任参数，JSON；复算的唯一依据）。 */
  @Column(name = "formula_snapshot", nullable = false)
  private String formulaSnapshot;

  /** 快照哈希（复算校验：同输入同输出）。 */
  @Column(name = "formula_hash", nullable = false)
  private String formulaHash;

  /** 理算合计（= Σ 明细实付）。 */
  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  /**
   * 本次实际消抵的年度免赔（{@code ⑩} 跨案累计的<b>写侧增量</b>，不落库）。
   *
   * <p>由理算内核产出、由 A7 结算经 {@code QuotaApplicationService} 累加进责任账 {@code
   * deductible_used}——不回写就没有次案，等于护栏只装了一半。
   */
  @Transient private BigDecimal deductibleApplied;

  /**
   * 本次理算所依据的跨案累计是否真实可读（⑩，不落库）。
   *
   * <p>{@code false} = 理算是在"已用未知"的前提下算出来的，结果仅供人工参考，A7 不得据此占额。 显式设字段而不是让上层去解析 {@code
   * formulaSnapshot}——解析快照字符串取值是"能跑通但口径会漂"的典型写法。
   */
  @Transient private boolean accumulationAvailable;

  @Column(name = "result_status", nullable = false)
  private String resultStatus;

  @Transient
  @Cascade(foreignKey = "calculationId", orphanRemoval = false)
  private List<AdjustmentLine> lines = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  // 审计列：基类（TenantAggregateRoot）不含审计字段（见其类注释「审计由子类自行声明」），
  // SDK 只映射 @Column 字段——漏声明会让 INSERT 不带 created_at/updated_at，
  // 而 0058 DDL 这两列 NOT NULL 无默认值，执行理算必然 1364。
  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private java.time.Instant updatedAt;

  private Adjustment(Long id, Long tenantId, Long claimId, String ruleVersion) {
    setId(id);
    setTenantId(tenantId);
    this.claimId = claimId;
    this.ruleVersion = ruleVersion == null ? "" : ruleVersion;
    this.resultStatus = ResultStatus.FROZEN.name();
    this.totalAmount = ZERO;
    this.deductibleApplied = ZERO;
    java.time.Instant now = java.time.Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * 执行理算（工厂方法，产出即冻结 {@code FROZEN}）。
   *
   * @param rows 费用明细（读侧投影，须已三段拆分：{@code validAmount}/{@code selfPay1} 就绪； 取自 {@code
   *     findClaimWithItems}——聚合 {@code findById} 不回填集合，与 blueprint 同纪律）
   * @param liability 当期责任配置（版本化，参数被物化进快照）
   * @param accumulation 跨案累计快照（⑩；必填，无默认重载）
   */
  public static Adjustment calculate(
      Long id,
      Long tenantId,
      Long claimId,
      List<ClaimWithItemsProjection> rows,
      AdjLiability liability,
      String ruleVersion,
      Accumulation accumulation) {
    if (rows == null || rows.isEmpty()) {
      throw new DomainException("理算明细不能为空");
    }
    if (liability == null || !liability.isActive()) {
      throw new DomainException("责任配置缺失或未生效，无法理算");
    }
    if (accumulation == null) {
      throw new DomainException("跨案累计快照缺失，无法理算（⑩：禁止按『已用 0』隐式计算）");
    }
    Adjustment adjustment = new Adjustment(id, tenantId, claimId, ruleVersion);
    adjustment.apply(rows, liability, accumulation);
    return adjustment;
  }

  /** 重算（同一聚合内重新计算；已确认 CONFIRMED 禁止调用，见 {@link #requireModifiable}）。 */
  public void recalculate(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    requireModifiable("已确认的理算结果禁止重算");
    apply(rows, liability, accumulation);
    this.updatedAt = java.time.Instant.now();
  }

  /** 核赔确认（frozen → claim，同额转移不二次占用）。 */
  public void confirm() {
    requireModifiable("已确认的理算结果不可重复确认");
    this.resultStatus = ResultStatus.CONFIRMED.name();
    this.updatedAt = java.time.Instant.now();
  }

  /** 解冻（释放冻结额度，回到可重算态）。 */
  public void unfreeze() {
    requireModifiable("已确认的理算结果禁止解冻（须先走冲正）");
    this.resultStatus = ResultStatus.UNFROZEN.name();
    this.updatedAt = java.time.Instant.now();
  }

  /** 回滚（拒赔/撤销：释放占用）。 */
  public void rollback() {
    requireModifiable("已确认的理算结果禁止直接回滚（须先走冲正）");
    this.resultStatus = ResultStatus.ROLLBACKED.name();
    this.updatedAt = java.time.Instant.now();
  }

  public ResultStatus resultStatusEnum() {
    return ResultStatus.valueOf(resultStatus);
  }

  /** 明细集合（包级可见；理由同 blueprint：{@code findById} 不回填集合，读侧走仓储投影）。 */
  List<AdjustmentLine> lines() {
    return Collections.unmodifiableList(lines);
  }

  // ===================== 理算内核 =====================

  private void apply(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    boolean socialOnly = liability.isSocialOnly();

    // ① 计算基准：合理费用合计；socialOnly 时剔除社保外（R-DIR-01）
    BigDecimal base = ZERO;
    BigDecimal selfPay1Total = ZERO;
    StringBuilder inputBuilder = new StringBuilder("[");
    for (ClaimWithItemsProjection row : rows) {
      if (row.getItemId() == null) {
        continue; // 无明细的纯头行跳过（LEFT JOIN 语义）
      }
      boolean inSocialScope = !socialOnly || "IN".equalsIgnoreCase(row.getItemSocialFlag());
      BigDecimal valid = nz(row.getItemValidAmount());
      if (inSocialScope) {
        base = base.add(valid);
      }
      selfPay1Total = selfPay1Total.add(nz(row.getItemSelfPay1()));
      inputBuilder
          .append("{\"item\":\"")
          .append(row.getItemName())
          .append("\",\"valid\":")
          .append(valid)
          .append(",\"inScope\":")
          .append(inSocialScope)
          .append("},");
    }
    if (inputBuilder.charAt(inputBuilder.length() - 1) == ',') {
      inputBuilder.deleteCharAt(inputBuilder.length() - 1);
    }
    inputBuilder.append("]");

    // ② 免赔（R-DED-03）：扣的是**剩余**免赔（配置 − 跨案已消抵），上限为基准本身，不得为负。
    //    按配置全额扣会让同保单第 2 案起每案重扣一次 ⇒ 系统性少赔。
    BigDecimal deductibleConfigured = liability.deduct();
    BigDecimal deductible = accumulation.remainingDeductible(deductibleConfigured).min(base);
    BigDecimal afterDeduct = base.subtract(deductible).max(ZERO);

    // ③ 赔付比例（clampRatioAt100：比例上限 100%。payPercent>100 视为配置错误，钳到 1.0 防超赔；
    //   对齐 reference-impl EngineFlags#clampRatioAt100，参考实现 D2/D3）
    BigDecimal ratio = liability.payRate().min(BigDecimal.ONE);
    BigDecimal ratioPayout = afterDeduct.multiply(ratio).setScale(CALC_SCALE, RoundingMode.HALF_UP);

    // ④ 限额截断（min 语义：任一维度耗尽即截断）
    //    截的是**剩余**责任额度（配置 − 跨案已占用 claimed+frozen）；按配置满额截会让第 2 案起每案重置 ⇒ 系统性超赔。
    BigDecimal limit = liability.limit();
    BigDecimal limitRemaining = accumulation.remainingLimit(limit);
    BigDecimal feePayout = limit.signum() > 0 ? ratioPayout.min(limitRemaining) : ratioPayout;
    feePayout = feePayout.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    BigDecimal feeRemain = limit.signum() > 0 ? limitRemaining.subtract(feePayout).max(ZERO) : ZERO;

    // ⑤ 零赔付归因（受控枚举，禁自由文本）
    String zeroReason = "";
    if (feePayout.signum() == 0) {
      if (base.signum() == 0) {
        zeroReason = "ZERO_REASONABLE";
      } else if (afterDeduct.signum() == 0) {
        zeroReason = "ZERO_DEDUCT";
      } else if (liability.payRate().signum() == 0) {
        zeroReason = "ZERO_PERCENT";
      } else if (limit.signum() > 0 && limitRemaining.signum() == 0) {
        // 与 ZERO_LIMIT 区分：本责任额度在本年度已被历史案件用尽（而非本案单次即超限）。
        // 拒赔五要素要求"事实认定"能说清是"本次超限"还是"年度用尽"，两者对客户的解释完全不同。
        zeroReason = "ZERO_LIMIT_EXHAUSTED";
      } else {
        zeroReason = "ZERO_LIMIT";
      }
    }

    String snapshot =
        "{"
            + "\"liabilityUuid\":\""
            + liability.getLiabilityUuid()
            + "\","
            + "\"liabilityVersion\":\""
            + liability.getLiabilityVersion()
            + "\","
            + "\"payPercent\":"
            + liability.getPayPercent()
            + ","
            + "\"deduct\":"
            + deductible
            + ","
            + // ⑩ 跨案累计：物化进快照，历史案件才能自包含复算（§5.2「物化不可仅版本引用」）。
            // 只存"本次扣了多少"而不存"扣之前已用多少"，复算时就无法区分"首案扣 1 万"与"次案扣 5000"。
            "\"deductibleBefore\":"
            + deductibleConfigured.setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"deductibleUsedBefore\":"
            + accumulation.deductibleUsed().setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"limit\":"
            + limit
            + ","
            + "\"limitUsedBefore\":"
            + accumulation.limitUsed().setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"accumulationAvailable\":"
            + accumulation.available()
            + ","
            + "\"socialOnly\":"
            + socialOnly
            + ","
            + "\"base\":"
            + base.setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"selfPay1Total\":"
            + selfPay1Total.setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"afterDeduct\":"
            + afterDeduct.setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"ratioPayout\":"
            + ratioPayout.setScale(RESULT_SCALE, RoundingMode.HALF_UP)
            + ","
            + "\"items\":"
            + inputBuilder
            + "}";

    this.liabilityUuid = liability.getLiabilityUuid();
    this.liabilityVersion = liability.getLiabilityVersion();
    this.formulaSnapshot = snapshot;
    this.formulaHash = sha256(snapshot);
    this.totalAmount = feePayout;
    this.deductibleApplied = deductible;
    this.accumulationAvailable = accumulation.available();
    this.lines = new ArrayList<>();
    AdjustmentLine outputLine =
        AdjustmentLine.of(
            null,
            getId(),
            liability.getLiabilityUuid(),
            "OUTPUT",
            inputBuilder.toString(),
            base.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
            deductible.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
            ratioPayout.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
            feePayout,
            feePayout,
            feeRemain.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
            zeroReason,
            "".equals(zeroReason) ? "" : reasonCodeOf(zeroReason),
            this.formulaHash);
    // 明细行随聚合落库，租户口径与聚合根一致（t_adj_calculation_line.tenant_id NOT NULL）
    outputLine.setTenantId(getTenantId());
    this.lines.add(outputLine);
  }

  /** 零赔付归因 → 受控原因码（§8.3 字典映射，禁自由文本）。 */
  private static String reasonCodeOf(String zeroReason) {
    return switch (zeroReason) {
      case "ZERO_DEDUCT" -> "06";
      case "ZERO_LIMIT", "ZERO_LIMIT_EXHAUSTED" -> "09";
      case "ZERO_PERCENT", "ZERO_REASONABLE" -> "05";
      default -> "";
    };
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private static String sha256(String input) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  private void requireModifiable(String message) {
    if (ResultStatus.CONFIRMED == resultStatusEnum()) {
      throw new DomainException(message + "（§5.3 硬不变量：已付额度禁止重复释放）");
    }
  }
}
