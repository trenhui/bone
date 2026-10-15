package com.bone.metadata.runtime.domain;

import java.util.List;

/**
 * 状态机动作定义（{@code meta_entity_action_def} 行的领域模型）。
 *
 * <p>一条定义描述一条「状态机边」：{@code from_status -> to_status}， 作用在 {@code status_field} 字段上；{@code
 * is_escape} 为真时表示有副作用的逃逸动作， 由 {@code handler_bean} 指定的 {@link
 * com.bone.metadata.runtime.application.RuntimeActionHandler} 执行副作用， 再由平台统一负责状态流转。
 *
 * <p>{@code entry}（0041）：<b>入口动作标记</b>——其 {@code from_status} 即新记录的初始状态（UML 状态图「初始状态伪状态」的等价物）。
 * 全环状态机（如设备巡检 {@code OPEN↔CLOSED}）没有「无入边」状态，图推导不出入口，必须由建模者显式声明； 未标记时回退到图推导（{@code from −
 * to}），仍不可推导则 fail-closed（拒绝创建，绝不采信客户端直写值）。
 */
public class RuntimeActionDef {

  private final long id;
  private final String entityCode;
  private final String actionCode;
  private final String label;
  private final String fromStatus;
  private final String toStatus;
  private final String statusField;
  private final boolean escape;
  private final String handlerBean;
  private final List<ActionParamSpec> params;
  private final String permissionCode;
  private final boolean requiresIdempotency;
  private final int sortOrder;
  private final boolean entry;

  public RuntimeActionDef(
      long id,
      String entityCode,
      String actionCode,
      String label,
      String fromStatus,
      String toStatus,
      String statusField,
      boolean escape,
      String handlerBean,
      List<ActionParamSpec> params,
      String permissionCode,
      boolean requiresIdempotency,
      int sortOrder,
      boolean entry) {
    this.id = id;
    this.entityCode = entityCode;
    this.actionCode = actionCode;
    this.label = label;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.statusField = statusField;
    this.escape = escape;
    this.handlerBean = handlerBean;
    this.params = params;
    this.permissionCode = permissionCode;
    this.requiresIdempotency = requiresIdempotency;
    this.sortOrder = sortOrder;
    this.entry = entry;
  }

  public long getId() {
    return id;
  }

  public String getEntityCode() {
    return entityCode;
  }

  public String getActionCode() {
    return actionCode;
  }

  public String getLabel() {
    return label;
  }

  /** 触发前记录的当前状态必须等于此值；{@code null} 表示「任意状态均可触发」。 */
  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public String getStatusField() {
    return statusField;
  }

  /** 逃逸动作（有副作用，如调外部支付网关）。 */
  public boolean isEscape() {
    return escape;
  }

  /** 逃逸动作的 Spring bean 名（实现 {@code RuntimeActionHandler}）。 */
  public String getHandlerBean() {
    return handlerBean;
  }

  public List<ActionParamSpec> getParams() {
    return params;
  }

  public String getPermissionCode() {
    return permissionCode;
  }

  /** 资金类动作需调用方带 {@code Idempotency-Key} 防重放。 */
  public boolean isRequiresIdempotency() {
    return requiresIdempotency;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  /**
   * 入口动作标记（0041）：{@code true} 时其 {@link #getFromStatus()} 即新记录的初始状态。
   *
   * <p>与 {@code from_status IS NULL}（任意状态可触发）是两件事——NULL-from 边不得当作入口候选， 入口必须显式声明（见 {@code
   * RuntimeStatusGuardTest#entryStatus_nullFromStatus_isNotTreatedAsEntry}）。
   */
  public boolean isEntry() {
    return entry;
  }
}
