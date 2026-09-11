package com.bone.platform.alert;

import com.bone.architecture.AggregatePureUnitTestGuard;
import org.junit.jupiter.api.Test;

/**
 * R8 聚合纯单测门禁（真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} E-3.1 R8 / E-8）。
 *
 * <p>本模块唯一聚合根 {@code NotificationMessage} 已具备合规纯单测（覆盖主状态机 + 拒绝路径 + 断言）， 故使用严格模式 {@code
 * verify}，无待补清单。
 */
class AggregatePureUnitTestCoverageTest {

  @Test
  void everyAggregateRootHasAPureUnitTest() {
    AggregatePureUnitTestGuard.verify("com.bone.platform.alert");
  }
}
