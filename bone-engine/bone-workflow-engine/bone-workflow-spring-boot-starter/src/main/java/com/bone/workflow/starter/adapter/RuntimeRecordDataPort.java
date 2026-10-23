package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import java.util.Map;
import java.util.Objects;

/**
 * 业务单据数据读取端口的平台实现：条件分支求值需要业务字段值（金额 / 部门 / 类目…）。
 *
 * <p>读的是<b>业务字段</b>，不含流程自身的元数据——引擎不该与业务表结构耦合。
 */
public class RuntimeRecordDataPort implements WorkflowRecordPort {

  private final JdbcRuntimeRecordService recordService;

  public RuntimeRecordDataPort(JdbcRuntimeRecordService recordService) {
    this.recordService = Objects.requireNonNull(recordService, "recordService");
  }

  @Override
  public Map<String, Object> loadRecordData(String entityCode, String recordId) {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流读取业务单据缺少租户上下文（TenantContext）——拒绝在未知租户下读取");
    }
    return recordService.getById(entityCode, tenantId, recordId);
  }
}
