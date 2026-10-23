package com.bone.workflow.starter.adapter;

import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;

/**
 * 流程 id 生成：复用平台 SDK 的分布式 id 生成器。
 *
 * <p>实例 / 任务 id 统一走平台 id 体系，避免引擎自造一套 id 规则（那会与平台雪花 id、审计对账脱节）。
 */
public class PlatformWorkflowIdGenerator implements WorkflowIdGenerator {

  private final SqlExecutor sqlExecutor;

  public PlatformWorkflowIdGenerator(SqlExecutor sqlExecutor) {
    this.sqlExecutor = sqlExecutor;
  }

  @Override
  public String nextId() {
    Object id = sqlExecutor.generateId(GenerationStrategy.DISTRIBUTED_ID, null);
    return String.valueOf(id);
  }
}
