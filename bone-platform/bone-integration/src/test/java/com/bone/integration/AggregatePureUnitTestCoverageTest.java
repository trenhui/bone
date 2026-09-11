package com.bone.integration;

import com.bone.architecture.AggregatePureUnitTestGuard;
import org.junit.jupiter.api.Test;

/**
 * R8 聚合纯单测门禁（真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} E-3.1 R8 / E-8）。
 *
 * <p>{@code IntegrationOutboxRecord} 属 Outbox 技术对象（无领域不变量），已下沉到 {@code
 * infrastructure/messaging/outbox}（与 bone-blueprint 同约定），不计入门禁。
 *
 * <p>存量 5 个聚合（{@code Connector}/{@code FlowConnection}/{@code FlowNode}/{@code
 * IntegrationFlow}/{@code IntegrationLog}）纯单测已补齐（2026-09-05），门禁由 {@code verifyAllowingPending}
 * 升级为<strong>严格模式</strong>：任何聚合根缺无容器纯单测都会立即失败。
 */
class AggregatePureUnitTestCoverageTest {

  @Test
  void everyAggregateRootHasAPureUnitTest() {
    AggregatePureUnitTestGuard.verify("com.bone.integration");
  }
}
