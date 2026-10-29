package com.bone.metadata.runtime.domain;

import java.util.Map;

/** 动作执行上下文：一次动作触发的全部输入，透传给 {@code RuntimeActionHandler}（逃逸副作用）。 */
public class RuntimeActionContext {

  /**
   * 副作用结果里的<b>平台状态流转改判键</b>：处理器返回该键时，平台按声明的语义处理 status 流转。
   *
   * <p><b>为什么需要它</b>：动作定义（{@code meta_entity_action_def}）只有单一 {@code to_status}，平台对逃逸动作 一律把 status
   * 推到 {@code to_status}。但「驳回退回指定节点」这类动作的真实语义是<b>原地返工</b>—— 引擎把实例退回上游节点并重新派单，单据本身还在审批中，status 必须保持
   * {@code SUBMITTED}。 接入前实测到的后果是：单据被推到 {@code REJECTED}（状态机终态），而实例仍 RUNNING、待办还在 ⇒ 审批人再点「通过」恒 409
   * {@code ILLEGAL_TRANSITION}，<b>待办永远清不掉的死单</b>。
   *
   * <p><b>契约边界</b>：只有服务端受信处理器能声明（结果来自 {@code handler.execute}，不接受调用方入参），
   * 状态机校验、字段级裁剪、审计留痕全部不变，仅跳过「status 推进」这一步；动作执行审计行照常写入。
   */
  public static final String OUTCOME_PLATFORM_STATUS = "platformStatus";

  /** {@link #OUTCOME_PLATFORM_STATUS} 取值：本次动作<b>不推进</b>平台 status（语义由处理器保证自洽）。 */
  public static final String PLATFORM_STATUS_KEEP = "KEEP";

  private final String entityCode;
  private final long tenantId;
  private final String recordId;
  private final RuntimeActionDef def;
  private final Map<String, Object> params;
  private final String operatorId;

  public RuntimeActionContext(
      String entityCode,
      long tenantId,
      String recordId,
      RuntimeActionDef def,
      Map<String, Object> params,
      String operatorId) {
    this.entityCode = entityCode;
    this.tenantId = tenantId;
    this.recordId = recordId;
    this.def = def;
    this.params = params;
    this.operatorId = operatorId;
  }

  public String getEntityCode() {
    return entityCode;
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getRecordId() {
    return recordId;
  }

  public RuntimeActionDef getDef() {
    return def;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public String getOperatorId() {
    return operatorId;
  }

  /**
   * 副作用是否声明「不推进平台 status」。
   *
   * <p>集中成一个判据而不是让调用点各自比较字符串：拼错 {@code "KEEP"} 会静默退回「照常推进」， 而那正是死单的成因——宁可只有一处可改。
   */
  public static boolean keepsPlatformStatus(Map<String, Object> sideEffect) {
    if (sideEffect == null) {
      return false;
    }
    Object value = sideEffect.get(OUTCOME_PLATFORM_STATUS);
    return value != null && PLATFORM_STATUS_KEEP.equalsIgnoreCase(value.toString().trim());
  }
}
