package com.bone.blueprint.application.integration.event;

import java.time.Instant;

/**
 * 跨边界集成事件的标准化信封。
 *
 * <p>所有发往基础设施 Outbox / MQ 的集成事件 record 须实现此接口， 基础设施侧可统一读取发生时刻、租户、契约版本等元数据，消除 instanceof 分支。
 */
public interface IntegrationEnvelope {

  /**
   * 载荷契约的当前版本。
   *
   * <p><b>与信封版本不是同一个槽位</b>：{@code OrderOutboxEnvelopeFactory} 的 {@code schemaVersion}
   * 描述<em>投递封装</em>格式， 本常量描述<em>事件载荷</em>结构，二者独立演进。此前 5 个事件各自声明同值常量，改版本要改 5 处且漏改无提示，
   * 故收成此处一份；某个事件需要独立版本时，在自己的 record 里覆盖 {@link #schemaVersion()} 即可。
   */
  String CURRENT_SCHEMA_VERSION = "1.0";

  /** 事实发生时刻（= 领域事件注册时刻），供下游排序与对账。 */
  Instant occurredAt();

  /** 事件携带的租户 ID（可能为 null）。 */
  Long tenantId();

  /** 契约 schema 版本。 */
  String schemaVersion();
}
