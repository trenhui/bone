package com.bone.procurement.governance.domain.model.capability;

import java.time.Instant;

/**
 * 全域 AI 一键停用开关（D3-v2 §3.1 C-4 / 蓝图 F3）。
 *
 * <p>覆盖 M1–M7 与编排层的统一停用；关闭（ENGAGED）时所有 AI 推理路径必须切到纯规则 / 人工，且不得残留自动结论。开关状态与操作人、理由、时间写入审计；
 * 缺省/不可判定视为已停用（fail-closed），只有显式 {@link #DISENGAGED} 才允许 AI 路径进入——即「默认全停，显式才开」。
 *
 * <p>读库即时生效：本对象是开关快照，由应用服务每次裁决时从仓储读取最新状态，无缓存（借鉴 claim 侧 kill switch 范式）。
 */
public final class GlobalKillSwitch {

  /** STANDBY=未启用 AI（缺省）；ENGAGED=已停用全部 AI；DISENGAGED=显式解除停用。 */
  public enum Mode {
    STANDBY,
    ENGAGED,
    DISENGAGED;

    /** 是否允许 AI 推理路径进入：只有显式解除停用才允许。 */
    public boolean allowsAi() {
      return this == DISENGAGED;
    }
  }

  private Mode mode;
  private String operator;
  private String reason;
  private Instant changedAt;
  private String drillRef;

  private GlobalKillSwitch(Mode mode, String operator, String reason, String drillRef) {
    this.mode = mode;
    this.operator = operator;
    this.reason = reason;
    this.changedAt = Instant.now();
    this.drillRef = drillRef;
  }

  /** 初始态：STANDBY（未启用，缺省不放行 AI）。 */
  public static GlobalKillSwitch standby() {
    return new GlobalKillSwitch(Mode.STANDBY, "system", "初始缺省", null);
  }

  /** 一键停用：任意态可转入 ENGAGED，停机优先于可用性。 */
  public void engage(String operator, String reason, String drillRef) {
    requireOperator(operator);
    this.mode = Mode.ENGAGED;
    this.operator = operator;
    this.reason = reason == null ? "" : reason;
    this.drillRef = drillRef;
    this.changedAt = Instant.now();
  }

  /** 解除停用：仅 ENGAGED 可显式恢复为 DISENGAGED。 */
  public void disengage(String operator, String reason) {
    requireOperator(operator);
    if (mode != Mode.ENGAGED) {
      throw new IllegalStateException("只有已停用态可解除，当前=" + mode);
    }
    this.mode = Mode.DISENGAGED;
    this.operator = operator;
    this.reason = reason == null ? "" : reason;
    this.drillRef = null;
    this.changedAt = Instant.now();
  }

  /** 缺省 fail-closed：非显式 DISENGAGED 一律不允许 AI 路径。 */
  public boolean allowsAi() {
    return mode.allowsAi();
  }

  private static void requireOperator(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("kill switch 操作必须记录操作人");
    }
  }

  public Mode mode() {
    return mode;
  }

  public String operator() {
    return operator;
  }

  public String reason() {
    return reason;
  }

  public Instant changedAt() {
    return changedAt;
  }

  public String drillRef() {
    return drillRef;
  }
}
