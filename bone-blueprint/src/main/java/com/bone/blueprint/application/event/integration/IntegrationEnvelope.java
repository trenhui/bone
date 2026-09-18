package com.bone.blueprint.application.event.integration;

import java.time.Instant;

/**
 * 跨边界集成事件的标准化信封。
 *
 * <p>所有发往基础设施 Outbox / MQ 的集成事件 record 须实现此接口， 基础设施侧可统一读取发生时刻、租户、契约版本等元数据，消除 instanceof 分支。
 */
public interface IntegrationEnvelope {

  /** 事实发生时刻（= 领域事件注册时刻），供下游排序与对账。 */
  Instant occurredAt();

  /** 事件携带的租户 ID（可能为 null）。 */
  Long tenantId();

  /** 契约 schema 版本。 */
  String schemaVersion();
}
