package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 额度控额栈（V1 §1.2 七层 + §7.2 五种扣减方式）。
 *
 * <p><b>为什么"用责任额度取 min"不够</b>：团体保单的赔付同时受 7 层额度约束——发票费用项目、发票费用类型、
 * 责任次限额、责任共保、责任、险种、计划。少任何一层都会超赔，而且超赔不会在单案上暴露： 每案都"看起来对"，直到计划总额被穿透。V1 明确给出 7 层顺序，本类就是这 7 层的执行器。
 *
 * <p><b>五种扣减方式为什么不能合并</b>（§7.2）：它们的公式不同，且差异是金额级的：
 *
 * <table>
 *   <caption>扣减方式的数学差别</caption>
 *   <tr><th>方式<th>公式<th>与方式 2 的差别
 *   <tr><td>1 统一额度-责任额度<td>min(发票责任理算, 责任可用)<td>不做分组截断
 *   <tr><td>2 责任+就诊类别<td>min(Σmin(该类, 该类可用), 责任可用)<td>基准
 *   <tr><td>3 责任+费用项目<td>min(Σmin(该项目, 该项目可用), 责任可用)<td>分组维度更细
 *   <tr><td>4 个人额度<td>Σmin(项目, 个人可用)<td>无责任层，个人池逐项消耗
 *   <tr><td>5 个人+责任<td>Σmin(项目, 个人可用, 责任可用)<td>两个池都逐项消耗
 * </table>
 *
 * <p>把 1/2/3 合并成"总额取 min"，会在"某一类费用额度用尽"时吞掉另一类的可用额度（系统性少赔）； 把 5
 * 合并成"总和后截断"会让先列的组优先占用池（顺序敏感但结果不同）。故此处按方式分支，不做归一化近似。
 */
public final class QuotaStack {

  /** 无限额度的比较哨兵（不是业务金额，只用于"未配置该层"的比较）。 */
  private static final BigDecimal UNBOUNDED = new BigDecimal("1E18");

  /** 额度层（V1 §1.2，从细到粗；{@code PERSONAL} 是 §1.3 的个账维度，只在方式 4/5 参与）。 */
  public enum Layer {
    /** 个账（§1.3）：单被保险人在所涉责任上的可赔额度，方式 4/5 的消耗池。 */
    PERSONAL,
    /** ① 发票费用项目可用金额。 */
    FEE_ITEM,
    /** ② 发票费用类型可用金额。 */
    FEE_TYPE,
    /** ③ 责任次限额金额。 */
    OCCURRENCE,
    /** ④ 责任共保可用金额。 */
    SHARED,
    /** ⑤ 责任可用金额。 */
    LIABILITY,
    /** ⑥ 险种可用金额。 */
    PRODUCT,
    /** ⑦ 计划可用金额。 */
    PLAN
  }

  /** 扣减方式（§7.2 五种）。 */
  public enum DeductionMode {
    /** 方式 1：发票单责任赔付 = min(理算金额, 责任可用额度)。 */
    LIABILITY_ONLY(Layer.LIABILITY, false),
    /** 方式 2：按费用类型分组截断，再责任额度截断。 */
    LIABILITY_PLUS_FEE_TYPE(Layer.FEE_TYPE, false),
    /** 方式 3：按费用项目分组截断，再责任额度截断。 */
    LIABILITY_PLUS_FEE_ITEM(Layer.FEE_ITEM, false),
    /** 方式 4：个人额度逐项消耗，无责任层。 */
    PERSONAL_ONLY(null, true),
    /** 方式 5：个人额度与责任额度都逐项消耗。 */
    PERSONAL_PLUS_LIABILITY(Layer.LIABILITY, true);

    private final Layer groupLayer;
    private final boolean sequential;

    DeductionMode(Layer groupLayer, boolean sequential) {
      this.groupLayer = groupLayer;
      this.sequential = sequential;
    }

    /** 该方式下"分组截断"使用的额度层（{@code null} = 不分组）。 */
    public Layer groupLayer() {
      return groupLayer;
    }

    /** 是否逐组顺序消耗额度池（方式 4/5），而非"求和后统一截断"（方式 1/2/3）。 */
    public boolean sequential() {
      return sequential;
    }
  }

  /** 一个待截断的组（费用类型桶 / 费用项目 / 次分组）。 */
  public record Group(String key, BigDecimal amount) {

    public Group {
      amount = Money.nonNegative(amount);
    }
  }

  /** 一层可用额度。{@code available} 为 {@code null} 表示无限（未配置该层）。 */
  public record LayerQuota(Layer layer, String key, BigDecimal available) {

    public boolean unlimited() {
      return available == null;
    }

    /** 可用额度（无限层用一个足够大的哨兵参与比较，避免调用方到处判空）。 */
    public BigDecimal amount() {
      return available == null ? UNBOUNDED : Money.nonNegative(available);
    }
  }

  /** 控额需求。 */
  public record Demand(
      List<Group> groups,
      Map<String, BigDecimal> groupCaps,
      BigDecimal personalCap,
      BigDecimal occurrenceCap) {

    public Demand {
      groups = List.copyOf(groups);
      groupCaps = groupCaps == null ? Map.of() : Map.copyOf(groupCaps);
    }

    public static Demand of(List<Group> groups) {
      return new Demand(groups, Map.of(), null, null);
    }

    /** 附次限额（层③，作用于本组总额，早于责任层⑤）。 */
    public Demand withOccurrenceCap(BigDecimal cap) {
      return new Demand(groups, groupCaps, personalCap, cap);
    }
  }

  /** 一层的截断记录（归因：这笔钱被哪一层截掉了）。 */
  public record Step(
      Layer layer, String key, BigDecimal before, BigDecimal available, BigDecimal lost) {}

  /** 组结果（{@code cappedBy} 记录该组是被哪一层截断的，用于归因）。 */
  public record GroupResult(String key, BigDecimal gross, BigDecimal payable, String cappedBy) {}

  /** 控额结果。 */
  public record Result(
      List<GroupResult> groups, BigDecimal payable, List<Step> steps, DeductionMode mode) {

    public Result {
      groups = List.copyOf(groups);
      steps = List.copyOf(steps);
    }

    /** 是否被任一层截断。 */
    public boolean truncated() {
      return !steps.isEmpty();
    }

    /** 被截掉的总额。 */
    public BigDecimal totalLost() {
      BigDecimal lost = Money.ZERO;
      for (Step step : steps) {
        lost = lost.add(step.lost());
      }
      return lost;
    }

    /** 首个生效的截断层（用于零赔付/少赔归因）。 */
    public Layer firstBindingLayer() {
      return steps.isEmpty() ? null : steps.get(0).layer();
    }
  }

  private QuotaStack() {}

  /**
   * 执行控额。
   *
   * <p><b>次限额为什么在这里统一预截断</b>：它是层③（V1 §7.2），作用于<b>本「次」的总额</b>，早于责任层⑤。 两种扣减方式（求和后截断 /
   * 逐组顺序消耗）若各自实现一次，就会出现"方式 1 生效、方式 5 忘掉"的分叉； 更糟的是方式 4/5 若把它放到池消耗之后，个人/责任池会被截断前的金额占用——多占的额度不会退。
   * 故此处只做一次：先按次限额把各组额度等比例压低，再进入分组/逐层逻辑。
   *
   * @param demand 待控额的分组金额 + 组额度 + 个人额度 + 次限额
   * @param layers 有序额度层（从细到粗；顺序即截断顺序，调用方须按 {@link Layer} 声明序排列）
   * @param mode 扣减方式
   */
  public static Result cap(Demand demand, List<LayerQuota> layers, DeductionMode mode) {
    if (demand.groups().isEmpty()) {
      return new Result(List.of(), Money.ZERO, List.of(), mode);
    }
    List<Step> pre = new ArrayList<>();
    Demand effective = applyOccurrenceCap(demand, pre);
    Result result =
        mode.sequential()
            ? capSequential(effective, layers, mode)
            : capAggregate(effective, layers, mode);
    if (pre.isEmpty()) {
      return result;
    }
    List<Step> steps = new ArrayList<>(pre);
    steps.addAll(result.steps());
    return new Result(result.groups(), result.payable(), steps, mode);
  }

  /** 层③ 责任次限额：本组总额超过次限额时按各组原额等比压低（不改变组间相对关系）。 */
  private static Demand applyOccurrenceCap(Demand demand, List<Step> steps) {
    BigDecimal cap = demand.occurrenceCap();
    if (cap == null || cap.signum() <= 0) {
      return demand;
    }
    List<BigDecimal> amounts = new ArrayList<>(demand.groups().size());
    for (Group group : demand.groups()) {
      amounts.add(group.amount());
    }
    BigDecimal total = Money.sum(amounts);
    if (total.compareTo(cap) <= 0) {
      return demand;
    }
    steps.add(new Step(Layer.OCCURRENCE, "OCCURRENCE", total, cap, total.subtract(cap)));
    List<BigDecimal> shares = Money.proRata(cap, amounts);
    List<Group> capped = new ArrayList<>(demand.groups().size());
    for (int i = 0; i < demand.groups().size(); i++) {
      capped.add(new Group(demand.groups().get(i).key(), shares.get(i)));
    }
    return new Demand(capped, demand.groupCaps(), demand.personalCap(), null);
  }

  /** 方式 1/2/3：组级截断 → 求和 → 逐层截断（每次截断后按原比例回分摊，保证 Σ组 ≡ 总额）。 */
  private static Result capAggregate(Demand demand, List<LayerQuota> layers, DeductionMode mode) {
    List<Step> steps = new ArrayList<>();
    List<BigDecimal> current = new ArrayList<>();
    List<Group> groups = demand.groups();

    // ① 组级截断（对应 §4.3 的两个 Min 内层：医保范围内 / 范围外各自的可用额度）
    for (Group group : groups) {
      BigDecimal cap = demand.groupCaps().get(group.key());
      BigDecimal amount = group.amount();
      if (cap != null && cap.signum() >= 0 && cap.compareTo(amount) < 0) {
        steps.add(
            new Step(
                mode.groupLayer() == null ? Layer.FEE_TYPE : mode.groupLayer(),
                group.key(),
                amount,
                cap,
                amount.subtract(cap)));
        amount = Money.nonNegative(cap);
      }
      current.add(amount);
    }

    // ② 逐层（责任 → 共保 → 险种 → 计划）截断
    for (LayerQuota layer : layers) {
      BigDecimal total = Money.sum(current);
      if (layer.unlimited() || layer.amount().compareTo(total) >= 0) {
        continue;
      }
      BigDecimal capped = layer.amount();
      steps.add(new Step(layer.layer(), layer.key(), total, capped, total.subtract(capped)));
      current = Money.proRata(capped, current);
    }

    return build(groups, current, steps, mode);
  }

  /** 方式 4/5：逐组顺序消耗额度池（个人额度、以及方式 5 的责任额度）。 */
  private static Result capSequential(Demand demand, List<LayerQuota> layers, DeductionMode mode) {
    List<Step> steps = new ArrayList<>();
    List<BigDecimal> current = new ArrayList<>();
    List<Group> groups = demand.groups();

    BigDecimal personalPool =
        demand.personalCap() == null ? null : Money.nonNegative(demand.personalCap());
    BigDecimal liabilityPool = null;
    if (mode == DeductionMode.PERSONAL_PLUS_LIABILITY) {
      for (LayerQuota layer : layers) {
        if (layer.layer() == Layer.LIABILITY && !layer.unlimited()) {
          liabilityPool = layer.amount();
        }
      }
    }

    for (Group group : groups) {
      BigDecimal amount = group.amount();
      BigDecimal groupCap = demand.groupCaps().get(group.key());
      if (groupCap != null && groupCap.compareTo(amount) < 0) {
        steps.add(
            new Step(
                mode.groupLayer() == null ? Layer.PERSONAL : mode.groupLayer(),
                group.key(),
                amount,
                groupCap,
                amount.subtract(groupCap)));
        amount = Money.nonNegative(groupCap);
      }
      if (personalPool != null && personalPool.compareTo(amount) < 0) {
        steps.add(
            new Step(
                Layer.PERSONAL, group.key(), amount, personalPool, amount.subtract(personalPool)));
        amount = personalPool;
        personalPool = Money.ZERO;
      } else if (personalPool != null) {
        personalPool = personalPool.subtract(amount);
      }
      if (liabilityPool != null && liabilityPool.compareTo(amount) < 0) {
        steps.add(
            new Step(
                Layer.LIABILITY,
                group.key(),
                amount,
                liabilityPool,
                amount.subtract(liabilityPool)));
        amount = liabilityPool;
        liabilityPool = Money.ZERO;
      } else if (liabilityPool != null) {
        liabilityPool = liabilityPool.subtract(amount);
      }
      current.add(Money.nonNegative(amount));
    }

    // 上层额度（共保 / 险种 / 计划）仍按"求和后截断"处理：它们控的是保单总额，不分组
    for (LayerQuota layer : layers) {
      if (layer.layer() == Layer.LIABILITY && mode == DeductionMode.PERSONAL_PLUS_LIABILITY) {
        continue; // 已逐组消耗
      }
      BigDecimal total = Money.sum(current);
      if (layer.unlimited() || layer.amount().compareTo(total) >= 0) {
        continue;
      }
      BigDecimal capped = layer.amount();
      steps.add(new Step(layer.layer(), layer.key(), total, capped, total.subtract(capped)));
      current = Money.proRata(capped, current);
    }

    return build(groups, current, steps, mode);
  }

  private static Result build(
      List<Group> groups, List<BigDecimal> amounts, List<Step> steps, DeductionMode mode) {
    List<GroupResult> results = new ArrayList<>(groups.size());
    BigDecimal payable = Money.ZERO;
    for (int i = 0; i < groups.size(); i++) {
      Group group = groups.get(i);
      BigDecimal amount = Money.round(amounts.get(i));
      results.add(
          new GroupResult(group.key(), group.amount(), amount, bindingKey(steps, group.key())));
      payable = payable.add(amount);
    }
    return new Result(results, Money.round(payable), steps, mode);
  }

  private static String bindingKey(List<Step> steps, String groupKey) {
    for (Step step : steps) {
      if (groupKey.equals(step.key())) {
        return step.layer().name() + ":" + step.key();
      }
    }
    for (Step step : steps) {
      if (!groupKey.equals(step.key())) {
        return step.layer().name();
      }
    }
    return "";
  }

  /**
   * 责任额度这一层的快捷构造（V1 §4.3 最外层 Min）。
   *
   * <p>{@code configured <= 0} 在责任配置里表示<b>不封顶</b>（reference-impl S25），故返回 {@code null} 可用额（无限），而不是
   * 0——把"不封顶"当成"额度已耗尽"会让所有未配额度的责任一分不赔。
   */
  public static LayerQuota liabilityLayer(String key, BigDecimal configured, BigDecimal used) {
    if (configured == null || configured.signum() <= 0) {
      return new LayerQuota(Layer.LIABILITY, key, null);
    }
    return new LayerQuota(
        Layer.LIABILITY, key, Money.nonNegative(configured.subtract(Money.nz(used))));
  }

  /** 分组键 → 金额的便捷索引（诊断用）。 */
  public static Map<String, BigDecimal> index(List<GroupResult> groups) {
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    for (GroupResult group : groups) {
      map.put(group.key(), group.payable());
    }
    return map;
  }
}
