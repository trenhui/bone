package com.bone.claim.domain.model.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 放量策略（{@code feature_toggle.config_json} 的解析结果，G3-1）。
 *
 * <p><b>为什么把「阶段 + 比例 + 预算阈值」放在一列 JSON 里而不是逐字段建列</b>：这三项会随治理口径变化（SLO 目标、最小样本、 观察窗），逐字段建列意味着「调整一次 SLO
 * 就要发一次 DDL」；而 {@code config_json} 本来就是为「按特性不同而不同的附带参数」 准备的（见 {@link
 * FeatureToggle#getConfigJson()}）。代价是解析必须宽容且有据可查，见 {@link #apply(Map)}。
 *
 * <p><b>案件级分桶，而不是租户级</b>：既有的 {@code rollout_pct} 按 {@code floorMod(tenantId, 100)} 判定，
 * 在<b>单租户部署</b>（本方案 MVP 的形态：一个保司一个租户）下会退化成「要么 0%、要么 100%」——灰度比例形同虚设。 因此 {@link #admitsCase(long,
 * long)} 用 {@code (tenantId, claimId)} 混合后取模，让同一租户内的案件可被稳定地分成
 * 两个桶：命中的自动出金、未命中的照旧走人工。同一案件多次调用结果必须一致（幂等），否则重试会「时放时不放」。
 */
public record AiRolloutPolicy(
    AiRolloutStage stage,
    int rolloutPct,
    BigDecimal errorRateTarget,
    int minInspectedSamples,
    int observationDays) {

  /** 代码基线：SHADOW + 2% 错误率目标 + 30 个已抽检样本 + 7 天观察窗。 */
  private static final AiRolloutStage DEFAULT_STAGE = AiRolloutStage.SHADOW;

  private static final BigDecimal DEFAULT_ERROR_RATE_TARGET = new BigDecimal("0.02");
  private static final int DEFAULT_MIN_INSPECTED_SAMPLES = 30;
  private static final int DEFAULT_OBSERVATION_DAYS = 7;

  /** 观察窗上界（与其他运维端点一致：不得退化成全库扫描器）。 */
  public static final int MAX_OBSERVATION_DAYS = 31;

  public static final String KEY_STAGE = "stage";
  public static final String KEY_ERROR_RATE_TARGET = "errorRateTarget";
  public static final String KEY_MIN_INSPECTED_SAMPLES = "minInspectedSamples";
  public static final String KEY_OBSERVATION_DAYS = "observationDays";

  /** 零配置基线：不放量、不误判（与「未配置即 SHADOW」完全一致）。 */
  public static AiRolloutPolicy defaults() {
    return new AiRolloutPolicy(
        DEFAULT_STAGE,
        0,
        DEFAULT_ERROR_RATE_TARGET,
        DEFAULT_MIN_INSPECTED_SAMPLES,
        DEFAULT_OBSERVATION_DAYS);
  }

  /**
   * 解析 + 校验配置。
   *
   * <p><b>逐键回落，不是一个键坏了就整段作废</b>：例如只把 {@code stage} 写成 {@code CANARY}（正确）同时把 {@code errorRateTarget}
   * 写成 {@code 2%}（不合法），若整段作废则阶段回到 SHADOW，配置者的意图（灰度） 与实际（完全不放量）相反且只有一条
   * WARN；逐键回落则「能用的生效、不能用的登记」，现象与配置意图最接近。
   *
   * <p><b>但 {@code stage} 永不因为其它键非法而回落到比配置更「宽」的档</b>——它只按自身取值解析，非法才回落 SHADOW。
   *
   * @param raw 解析后的 JSON 对象（可为 null）
   * @return 生效策略 + 未生效键（永不返回 null）
   */
  public static Resolution apply(Map<String, Object> raw) {
    return apply(raw, 0);
  }

  /**
   * 解析 + 校验配置（灰度比例以 {@code rollout_pct} 列为准）。
   *
   * <p><b>为什么 {@code rolloutPct} 只认列、不认 JSON</b>：列是既有开关 UI / 接口已经写入的口径，若 JSON 里再允许写一份， 就会出现「页面显示
   * 50%、实际生效 0%」这类双写分叉——而且两个值都「看起来对」，排查时无从判断哪个才是真源。 因此配置里出现 {@code rolloutPct}
   * 时<b>不采信、只登记</b>，提示改列。
   *
   * @param raw 解析后的 JSON 对象（可为 null）
   * @param columnRolloutPct {@code feature_toggle.rollout_pct} 列值（唯一口径）
   * @return 生效策略 + 未生效键（永不返回 null）
   */
  public static Resolution apply(Map<String, Object> raw, int columnRolloutPct) {
    int pct = Math.max(0, Math.min(100, columnRolloutPct));
    if (raw == null || raw.isEmpty()) {
      return new Resolution(
          new AiRolloutPolicy(
              DEFAULT_STAGE,
              pct,
              DEFAULT_ERROR_RATE_TARGET,
              DEFAULT_MIN_INSPECTED_SAMPLES,
              DEFAULT_OBSERVATION_DAYS),
          Map.of());
    }
    Map<String, String> rejected = new LinkedHashMap<>();
    if (raw.get("rolloutPct") != null) {
      rejected.put(
          "rolloutPct",
          "灰度比例以 rollout_pct 列为唯一口径（当前列值 " + pct + "），config_json 里的同名键不采信；两处并存会造成「页面显示与实际生效不一致」");
    }

    AiRolloutStage stage = parseStage(raw.get(KEY_STAGE), rejected);
    BigDecimal target = parseTarget(raw.get(KEY_ERROR_RATE_TARGET), rejected);
    int minSamples = parseMinSamples(raw.get(KEY_MIN_INSPECTED_SAMPLES), rejected);
    int days = parseDays(raw.get(KEY_OBSERVATION_DAYS), rejected);

    return new Resolution(new AiRolloutPolicy(stage, pct, target, minSamples, days), rejected);
  }

  /**
   * 本策略是否允许该案件自动出金（阶段闸 + 分桶闸）。
   *
   * <p><b>只判「允许」，不判「应当」</b>：金额上限、反欺诈硬闸、知识能力缺失兜底仍在各自的位置，本方法不重复也不越权。
   *
   * @param tenantId 租户
   * @param claimId 案件
   * @return true 表示本档允许该案件进入自动出金
   */
  public boolean admitsCase(long tenantId, long claimId) {
    if (!stage.permitsAutoSettlement()) {
      return false;
    }
    if (stage == AiRolloutStage.AUTOMATED) {
      return true;
    }
    if (rolloutPct >= 100) {
      return true;
    }
    if (rolloutPct <= 0) {
      return false;
    }
    return Math.floorMod(bucketKey(tenantId, claimId), 100) < rolloutPct;
  }

  /**
   * 分桶键：确定性混合，<b>不依赖任何运行时随机源</b>。
   *
   * <p>用 64 位黄金比例常数做乘法混合后取模：相邻 {@code claimId}（雪花 ID 低位连续递增）经乘法混合后低位分布被充分打散， 否则「id < N
   * 的都被抽中」会让灰度样本集中在某个时间段，等于变相按时间放量。
   */
  static long bucketKey(long tenantId, long claimId) {
    long mixed = (claimId * 0x9E3779B97F4A7C15L) ^ (tenantId * 0xC2B2AE3D27D4EB4FL);
    return Math.floorMod(mixed ^ (mixed >>> 32), 1_000_003L);
  }

  private static AiRolloutStage parseStage(Object value, Map<String, String> rejected) {
    if (value == null) {
      return DEFAULT_STAGE;
    }
    AiRolloutStage parsed = AiRolloutStage.parse(String.valueOf(value), null);
    if (parsed == null) {
      rejected.put(
          KEY_STAGE, "取值 " + value + " 不是受控档位（SHADOW/ASSISTED/CANARY/AUTOMATED）；已回落 SHADOW（不放量）");
      return DEFAULT_STAGE;
    }
    return parsed;
  }

  private static BigDecimal parseTarget(Object value, Map<String, String> rejected) {
    if (value == null) {
      return DEFAULT_ERROR_RATE_TARGET;
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      rejected.put(
          KEY_ERROR_RATE_TARGET, "取值 " + value + " 不是数字（注意不是百分数字符串，应写 0.02 而非 2%）；已回落 0.02");
      return DEFAULT_ERROR_RATE_TARGET;
    }
    // 必须是 (0,1)：0 意味着「一次差错都不许有」，任何真实抽样都会立刻熔断，等于把功能做死；
    // 1 意味着「错光了也不熔断」，熔断器名存实亡。两者都不是合法配置，回落基线并登记。
    if (parsed.compareTo(BigDecimal.ZERO) <= 0 || parsed.compareTo(BigDecimal.ONE) >= 0) {
      rejected.put(
          KEY_ERROR_RATE_TARGET, "取值 " + parsed + " 不在 (0,1) 开区间；0 会让功能一上线就熔断、1 等于没有熔断，已回落 0.02");
      return DEFAULT_ERROR_RATE_TARGET;
    }
    return parsed;
  }

  private static int parseMinSamples(Object value, Map<String, String> rejected) {
    if (value == null) {
      return DEFAULT_MIN_INSPECTED_SAMPLES;
    }
    Integer parsed = parseInt(value);
    if (parsed == null || parsed < 1) {
      rejected.put(
          KEY_MIN_INSPECTED_SAMPLES,
          "取值 " + value + " 不是正整数；已回落 30。该值决定「多少已抽检样本才允许判定超限」，" + "设为 1 会让单次差错立刻熔断");
      return DEFAULT_MIN_INSPECTED_SAMPLES;
    }
    return parsed;
  }

  private static int parseDays(Object value, Map<String, String> rejected) {
    if (value == null) {
      return DEFAULT_OBSERVATION_DAYS;
    }
    Integer parsed = parseInt(value);
    if (parsed == null || parsed < 1 || parsed > MAX_OBSERVATION_DAYS) {
      rejected.put(
          KEY_OBSERVATION_DAYS,
          "取值 " + value + " 不是 1-" + MAX_OBSERVATION_DAYS + " 的整数；已回落 " + DEFAULT_OBSERVATION_DAYS);
      return DEFAULT_OBSERVATION_DAYS;
    }
    return parsed;
  }

  private static Integer parseInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.valueOf(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 策略解析结果：生效策略 + 未生效键及原因（{@code $json} 伪键表示整段不是 JSON 对象）。 */
  public record Resolution(AiRolloutPolicy policy, Map<String, String> rejected) {

    public Resolution {
      rejected = rejected == null ? Map.of() : Map.copyOf(rejected);
    }

    /** 全部键都已生效。 */
    public boolean fullyApplied() {
      return rejected.isEmpty();
    }

    /** 未生效项的摘要（日志/端点用）。 */
    public String rejectionSummary() {
      return rejected.isEmpty() ? "" : rejected.toString();
    }
  }
}
