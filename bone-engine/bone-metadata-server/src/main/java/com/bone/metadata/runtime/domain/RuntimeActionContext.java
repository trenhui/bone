package com.bone.metadata.runtime.domain;

import java.util.Map;

/** 动作执行上下文：一次动作触发的全部输入，透传给 {@code RuntimeActionHandler}（逃逸副作用）。 */
public class RuntimeActionContext {

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
}
