package com.bone.procurement.governance.domain.model.capability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 能力登记聚合根：能力清单登记 + 具名责任人 + 一键停用/降级状态机 + 追溯链。
 *
 * <p>状态迁移（对齐 R-1/R-5 与监管停机演练）：
 *
 * <ul>
 *   <li>登记缺省为 {@link CapabilityState#UNKNOWN}（fail-closed，未显式启用不得推理）；
 *   <li>UNKNOWN → AVAILABLE（具名责任人显式启用）；
 *   <li>AVAILABLE → DISABLED / DEGRADED（一键停用或降级）；
 *   <li>DISABLED / DEGRADED → AVAILABLE（恢复需显式操作）；
 *   <li>UNDETERMINED 为不可判定态，不得迁出到 AVAILABLE 之外（保持隔离）。
 * </ul>
 *
 * <p>每次状态变更写入不可变追溯条目（谁、何时、何因），供审计与停机演练复盘。
 */
public final class AiCapability {

  private final CapabilityCode code;
  private final String displayName;
  private CapabilityState state;
  private OwnerRef owner;
  private final List<GovernanceTraceEntry> trace = new ArrayList<>();

  private AiCapability(CapabilityCode code, String displayName, OwnerRef owner) {
    this.code = Objects.requireNonNull(code, "code");
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("能力显示名不得为空");
    }
    this.displayName = displayName;
    this.owner = Objects.requireNonNull(owner, "owner");
    // 缺省 fail-closed：登记不等于启用。
    this.state = CapabilityState.UNKNOWN;
    this.trace.add(GovernanceTraceEntry.of(code.value(), "REGISTERED", "owner=" + owner.userId()));
  }

  /** 持久化重建构造器：由已落库快照恢复，不追加 REGISTERED 留痕。 */
  private AiCapability(
      CapabilityCode code,
      String displayName,
      CapabilityState state,
      OwnerRef owner,
      List<GovernanceTraceEntry> trace) {
    this.code = Objects.requireNonNull(code, "code");
    this.displayName = displayName;
    this.state = state == null ? CapabilityState.UNKNOWN : state;
    this.owner = Objects.requireNonNull(owner, "owner");
    this.trace.addAll(trace == null ? List.of() : trace);
  }

  /** 持久化重建工厂：仅供 infrastructure 适配器使用，业务代码不得绕过状态机。 */
  public static AiCapability rehydrate(
      CapabilityCode code,
      String displayName,
      CapabilityState state,
      OwnerRef owner,
      List<GovernanceTraceEntry> trace) {
    return new AiCapability(code, displayName, state, owner, trace);
  }

  /** 登记一个新能力；初始状态 UNKNOWN，需具名责任人显式启用。 */
  public static AiCapability register(CapabilityCode code, String displayName, OwnerRef owner) {
    return new AiCapability(code, displayName, owner);
  }

  public boolean allowsInference() {
    return state.allowsInference();
  }

  /** 启用：仅 UNKNOWN / DISABLED / DEGRADED 可迁移到 AVAILABLE；UNDETERMINED 不可直接启用。 */
  public void enable(String operator, String reason) {
    requireOperator(operator);
    if (state == CapabilityState.UNDETERMINED) {
      throw new IllegalStateException("不可判定态能力不得直接启用，需先复核: " + code.value());
    }
    if (state == CapabilityState.AVAILABLE) {
      return;
    }
    CapabilityState previous = state;
    this.state = CapabilityState.AVAILABLE;
    appendTrace("ENABLED", operator, reason, previous);
  }

  /** 一键停用：任意非 DISABLED 态均可停用（含 UNKNOWN/DEGRADED），停机优先于可用性。 */
  public void disable(String operator, String reason) {
    requireOperator(operator);
    if (state == CapabilityState.DISABLED) {
      return;
    }
    CapabilityState previous = state;
    this.state = CapabilityState.DISABLED;
    appendTrace("DISABLED", operator, reason, previous);
  }

  /** 降级：仅 AVAILABLE 可降级；降级后不再进入 AI 推理路径。 */
  public void degrade(String operator, String reason) {
    requireOperator(operator);
    if (state != CapabilityState.AVAILABLE) {
      throw new IllegalStateException("只有可用态能力可降级，当前=" + state);
    }
    this.state = CapabilityState.DEGRADED;
    appendTrace("DEGRADED", operator, reason, CapabilityState.AVAILABLE);
  }

  /** 标记为不可判定：任何态都可转入，用于纠纷/审计未决期间冻结能力。 */
  public void markUndetermined(String operator, String reason) {
    requireOperator(operator);
    CapabilityState previous = state;
    this.state = CapabilityState.UNDETERMINED;
    appendTrace("UNDETERMINED", operator, reason, previous);
  }

  /** 变更具名责任人（保留旧责任人于追溯链）。 */
  public void reassignOwner(OwnerRef newOwner, String operator, String reason) {
    requireOperator(operator);
    Objects.requireNonNull(newOwner, "newOwner");
    OwnerRef previous = this.owner;
    this.owner = newOwner;
    appendTrace("OWNER_REASSIGNED", operator, reason + " | from=" + previous.userId());
  }

  private void appendTrace(String event, String operator, String reason, CapabilityState previous) {
    this.trace.add(
        GovernanceTraceEntry.of(
            code.value(), event, operator + " | from=" + previous + " | reason=" + reason));
  }

  private void appendTrace(String event, String operator, String detail) {
    this.trace.add(GovernanceTraceEntry.of(code.value(), event, operator + " | " + detail));
  }

  private static void requireOperator(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("治理操作必须记录操作人（审计要求）");
    }
  }

  public CapabilityCode code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public CapabilityState state() {
    return state;
  }

  public OwnerRef owner() {
    return owner;
  }

  /** 返回追溯链只读副本，保证快照不可变。 */
  public List<GovernanceTraceEntry> trace() {
    return List.copyOf(trace);
  }

  public Instant registeredAt() {
    return trace.isEmpty() ? null : trace.get(0).occurredAt();
  }
}
