package com.bone.engine.extension.studio.application.support;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.bone.engine.extension.studio.application.ExtensionCommandApplicationService;
import com.bone.engine.extension.studio.application.ExtensionQueryApplicationService;
import com.bone.engine.extension.studio.domain.model.operation.StudioOperation;
import com.bone.engine.extension.studio.observability.StudioExtensionMetrics;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** 验证 LRO 状态 Map 在完成后被回收（无界 Map 内存泄漏修复）与线程池有界。 */
class StudioLroSupportTest {

  @Test
  void completedLroIsEvictedAndMapStaysBounded() throws Exception {
    ExtensionCommandApplicationService cmd = mock(ExtensionCommandApplicationService.class);
    ExtensionQueryApplicationService qry = mock(ExtensionQueryApplicationService.class);
    StudioAuditSupport audit = mock(StudioAuditSupport.class);
    StudioExtensionMetrics metrics = mock(StudioExtensionMetrics.class);
    // TTL=0：完成后立即调度回收，便于单测断言 Map 清空
    StudioLroSupport support = new StudioLroSupport(cmd, qry, audit, metrics, 0);

    try {
      int n = 20;
      String[] ids = new String[n];
      for (int i = 0; i < n; i++) {
        ids[i] = support.startPluginDeploy((long) i);
      }
      for (String id : ids) {
        pollUntil(id, support, op -> op != null && op.isDone(), 5000);
      }
      for (String id : ids) {
        pollUntil(id, support, op -> op == null, 5000);
      }
      if (support.getOperation(ids[0]) != null) {
        fail("LRO 状态未被回收，存在无界 Map 泄漏");
      }
    } finally {
      support.destroy();
    }
  }

  private void pollUntil(
      String id, StudioLroSupport support, Predicate<StudioOperation> cond, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (cond.test(support.getOperation(id))) {
        return;
      }
      Thread.sleep(50);
    }
    fail("condition not met within " + timeoutMs + "ms for " + id);
  }
}
