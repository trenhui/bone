package com.bone.blueprint;

import com.bone.architecture.AggregatePureUnitTestGuard;
import org.junit.jupiter.api.Test;

/**
 * R8 门禁：本模块每个具体聚合根都必须有一个无容器纯单测（主规范 R8）。
 *
 * <p>口径见 {@link AggregatePureUnitTestGuard}：同名 {@code *Test} 类存在 + 至少 1 个 {@code @Test} 方法 +
 * 不带容器注解。基础设施中的持久化记录（{@code OrderOutboxRecord}）属技术对象，不计入。
 */
class AggregatePureUnitTestCoverageTest {

  @Test
  void everyAggregateRootHasAPureUnitTest() {
    AggregatePureUnitTestGuard.verify("com.bone.blueprint");
  }
}
