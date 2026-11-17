package com.bone.claim.domain.model.config;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import lombok.Getter;

/**
 * 租户级特性开关与灰度（§11.4 三态开关）。
 *
 * <p><b>用途</b>：承载新能力的渐进发布与安全灰度（如 {@code AUTO_APPROVE} 自动放款按租户开启 / 按比例灰度）。
 * 未配置某特性的租户视为「继承全局开关」（fail-closed：默认不开启自动放款等资金策略，避免能力未就绪即放量）。
 *
 * <p><b>三态语义</b>：{@code ENABLED} 全量开启 / {@code DISABLED} 显式关闭 / 无记录＝继承全局默认。 {@code rollout_pct}
 * 为灰度比例（0–100），接入方按 {@code hash(tenantId) % 100 < rollout_pct} 判定是否命中灰度。
 */
@Getter
@Table("t_feature_toggle")
public class FeatureToggle extends TenantAbstractEntity<Long> {

  /** 自动放款特性键（§11.4 / AutoApprovalApplicationService）。 */
  public static final String FEATURE_AUTO_APPROVE = "AUTO_APPROVE";

  /**
   * 任务自动分配特性键（批 1 灰度开关）。
   *
   * <p><b>关闭时的行为是「停写归属」而不是「不建任务」</b>：新任务仍以入池形态（{@code PENDING_CLAIM}，
   * 无归属人）创建。若关闭时连任务都不建，关闭开关就等于把案件从待办里抹掉——那是数据丢失， 不是回滚。故开关只决定「是否执行规则求解」，不决定「任务是否存在」。
   */
  public static final String FEATURE_AUTO_ASSIGN = "AUTO_ASSIGN";

  /**
   * 分诊阈值特性键（§8.3 配置外部化 / §11.4 阈值外置）。
   *
   * <p>{@code config_json} 支持的键见 {@code TriageLimits} 的 {@code CONFIG_*} 常量；缺键即用代码常量。
   */
  public static final String FEATURE_TRIAGE_LIMITS = "TRIAGE_LIMITS";

  /**
   * 独立复审特性键（批 3 灰度开关，任务 4.1）。
   *
   * <p><b>关闭时零流量</b>：不产生任何新的 43/46 状态流转，44 双人复核与初审行为不变。开关只决定「是否放行独立复审入口」，
   * 不删除既有复审数据、不放宽职责分离（同人不可自审在任何开关态都硬拒绝，见 spec §职责分离）。
   */
  public static final String FEATURE_RE_REVIEW = "RE_REVIEW";

  /**
   * 抽检比例特性键（批 4 任务 4.1：从全局 {@code @Value} 迁入 t_feature_toggle 并上屏）。
   *
   * <p>{@code config_json} 的键为 {@code samplingRatePercent}（0–100 整数百分比）；缺记录 / 未开启 / 非法值一律回落 代码基线
   * 5，保证「配了不生效」不会静默改成 0（不抽样）或 100（全量），而是维持既有监管口径。
   */
  public static final String FEATURE_INSPECTION_SAMPLING = "INSPECTION_SAMPLING";

  /**
   * 抽检处置动作特性键（批 4 任务 4.3）。
   *
   * <p><b>关闭时只登记不流转</b>：处置留痕照写，但不生成重办任务、不改案件状态——与 {@code AUTO_ASSIGN} 关闭时 「停写归属但不抹掉任务」同款纪律。留痕行的
   * feature_enabled 字段自证当时的开关态。
   */
  public static final String FEATURE_INSPECTION_ACTION = "INSPECTION_ACTION";

  /**
   * 审核疲劳防控特性键（config_json 由 {@code ReviewFatigueConfig} 唯一解析）。
   *
   * <p>无记录 / 未开启 / 非法配置一律回落基线，不会把「查不到」当成「不受限」。
   */
  public static final String FEATURE_REVIEW_FATIGUE_GUARD = "REVIEW_FATIGUE_GUARD";

  public static final String STATE_ENABLED = "ENABLED";
  public static final String STATE_DISABLED = "DISABLED";

  /**
   * 当前已支持的开关状态全集。
   *
   * <p><b>为什么显式枚举而不是「非 ENABLED 即 DISABLED」</b>：{@code state} 列是自由 {@code varchar}，若读取侧只认 {@code
   * ENABLED}，那么任何拼写错误（{@code ENABLE} / {@code enabled} / {@code Enabled}）或未来新增的档位都会<b>静默
   * 当成「关闭」</b>——而「关闭」是 fail-closed 的安全方向，于是配置者以为开关已开、实际一直没开，<b>且没有任何
   * 痕迹</b>。这类「配了不生效且无人知晓」的缺陷比直接报错更难发现。
   *
   * <p>契约 §11.4 的 AI 介入三档（{@code SHADOW / ASSISTED / AUTOMATED}）属于<b>另一个维度</b>（AI 介入程度），
   * 不是本列的开关态——它们必须另设特性键或专用列，<b>不得复用本列</b>：{@link #isOn(long)} 只认 {@code ENABLED}，若把 {@code
   * AUTOMATED} 写进本列，会被当成关闭而使「自动化介入」静默失效。
   */
  private static final Set<String> SUPPORTED_STATES = Set.of(STATE_ENABLED, STATE_DISABLED);

  /**
   * 状态是否属于当前支持的取值。
   *
   * @param state 状态字符串（可为 null）
   * @return true 表示取值合法
   */
  public static boolean isSupportedState(String state) {
    return state != null && SUPPORTED_STATES.contains(state);
  }

  @Column(name = "feature_key", nullable = false)
  private String featureKey;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "rollout_pct", nullable = false)
  private Integer rolloutPct;

  @Column(name = "remark")
  private String remark;

  /**
   * 开关附带参数（JSON 对象，见 {@link #FEATURE_TRIAGE_LIMITS} 的键约定）。
   *
   * <p><b>为什么是一列 JSON 而不是逐字段建列</b>：附带参数按特性不同而不同（分诊阈值 / 灰度细分 / 预算上限），逐字段建列会让 「新增一个可配参数」变成 DDL
   * 变更；JSON 把可变结构收在值里，表结构只留一个稳定的承载位。
   *
   * <p><b>null 的语义是「未配置」而不是「空配置」</b>：消费方据此回落到代码常量，保证「不给配置 ⇒ 行为与上线前逐位一致」。 解析与校验在应用层（{@code
   * FeatureTenantToggleService}），领域层只承载原文——避免 domain 依赖 JSON 库。
   */
  @Column(name = "config_json")
  private String configJson;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  FeatureToggle() {}

  public static FeatureToggle create(
      Long tenantId, String featureKey, String state, int rolloutPct, String remark, Instant now) {
    return create(tenantId, featureKey, state, rolloutPct, remark, null, now);
  }

  public static FeatureToggle create(
      Long tenantId,
      String featureKey,
      String state,
      int rolloutPct,
      String remark,
      String configJson,
      Instant now) {
    // 写入期拒绝非法状态：让「配错」在入口就失败，而不是等到读侧静默当成关闭。
    // 错误消息带**后果**（不只说「不合法」），否则调用方不知道该怎么改。
    if (!isSupportedState(state)) {
      throw new IllegalArgumentException(
          "feature_toggle.state 取值非法：`"
              + state
              + "`。当前只支持 "
              + SUPPORTED_STATES
              + "（大小写敏感）。写入非法值的后果：读取侧只认 ENABLED，该开关会被**静默当成关闭**——"
              + "配置者以为已开启，实际从未生效且无任何痕迹。若本意是 AI 介入档位"
              + "（SHADOW/ASSISTED/AUTOMATED），请另设特性键或专用列，不要复用本列。");
    }
    if (rolloutPct < 0 || rolloutPct > 100) {
      throw new IllegalArgumentException(
          "feature_toggle.rollout_pct 越界：`"
              + rolloutPct
              + "`。合法范围 0-100。"
              + "越界值的后果：读取侧把 >=100 视为全量、<=0 视为全关，中间的非法值会按"
              + "floorMod(tenantId,100) < pct 判定，灰度比例与预期不符且难以察觉。");
    }
    FeatureToggle toggle = new FeatureToggle();
    toggle.setTenantId(tenantId);
    toggle.featureKey = featureKey;
    toggle.state = state;
    toggle.rolloutPct = rolloutPct;
    toggle.remark = remark == null ? "" : remark;
    toggle.configJson = configJson;
    toggle.version = 0L;
    // 审计列由 SDK 映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，必须显式赋值
    Date nowDate = Date.from(now);
    toggle.setCreatedAt(nowDate);
    toggle.setUpdatedAt(nowDate);
    return toggle;
  }

  /**
   * 覆写附带参数（放量阶段 / 预算阈值 / 回滚现场）。
   *
   * <p><b>为什么不新增 {@code setConfigJson}</b>：无约束的 setter 让任何调用方都能改配置而不用说明原因，事后在审计里 只剩「时间 +
   * 新值」，无法回答「谁在什么证据下把它降档的」。本方法强制随写一句 {@code auditNote}，并把它写进 {@code
   * remark}（该列本就是给运维备注用的），使回滚动作自带现场说明。
   *
   * <p><b>{@code null} 是合法入参</b>：语义是「清空附带参数、回落到代码基线」，与读取侧「null = 未配置」一致。
   *
   * @param configJson 新的 JSON 原文（null 表示清空）
   * @param auditNote 本次变更的现场说明（自动回滚时由调用方写入击穿指标）
   * @param now 变更时间
   */
  public void overwriteConfigJson(String configJson, String auditNote, Instant now) {
    this.configJson = configJson;
    this.remark = auditNote == null ? "" : auditNote;
    this.setUpdatedAt(Date.from(now));
  }

  /**
   * 是否处于开启态（ENABLED 且灰度命中）。
   *
   * <p><b>读取期的兜底口径</b>：状态不是 {@code ENABLED} 一律当关闭（fail-closed）。这一条<b>不能收紧</b>—— 收紧成「只对 DISABLED
   * 关闭、其余交给上层判断」会让未知值（如 {@code AUTOMATED}）变成 「不确定的开启」，反而把安全方向倒过来。合法的收紧方式是在<b>写入期</b>拒绝（见 {@link
   * #create}），读取期只负责兜底。
   */
  public boolean isOn(long tenantId) {
    if (!STATE_ENABLED.equals(state)) {
      return false;
    }
    if (rolloutPct == null || rolloutPct >= 100) {
      return true;
    }
    if (rolloutPct <= 0) {
      return false;
    }
    return Math.floorMod(tenantId, 100) < rolloutPct;
  }

  /**
   * 状态是否为已支持的取值（用于「配错了但读侧仍要兜底为关闭」时的可观测性）。
   *
   * <p>存在意义：读取侧对未知状态<b>只能</b>当关闭（安全方向），但「静默」本身是缺陷——运维配了个 {@code ENABLE}，开关永远不生效，页面也看不出异常。调用方据此打
   * WARN 日志，让「配错」可见。
   *
   * @return true 表示状态合法；false 表示这是历史脏数据或直连写入的非法值
   */
  public boolean hasSupportedState() {
    return isSupportedState(state);
  }
}
