package com.bone.engine.extension.studio;

import com.bone.architecture.AggregatePureUnitTestGuard;
import org.junit.jupiter.api.Test;

/**
 * R8 聚合纯单测门禁（真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} E-3.1 R8 / E-8）。
 *
 * <p>本模块当前<strong>无业务聚合根</strong>（领域模型为接口与描述对象，持久化走内存实现 + PO），故使用 严格模式 {@code verify}
 * 且无待补清单——<b>未来一旦引入聚合根，本门禁立即要求配套纯单测</b>，守住增量。
 */
class AggregatePureUnitTestCoverageTest {

  @Test
  void everyAggregateRootHasAPureUnitTest() {
    AggregatePureUnitTestGuard.verify("com.bone.engine.extension.studio");
  }
}
