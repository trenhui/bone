package com.bone.app.expense.workflow;

import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** 测试夹具：把 mock 包成 {@link ObjectProvider}（与生产惰性解析同形态）。 */
final class WorkflowFixtures {

  private WorkflowFixtures() {}

  static ObjectProvider<WorkflowEngine> engineProvider(WorkflowEngine engine) {
    DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
    bf.registerSingleton("workflowEngine", engine);
    return bf.getBeanProvider(WorkflowEngine.class);
  }

  static ObjectProvider<WorkflowStatusOutboxRepository> outboxProvider(
      WorkflowStatusOutboxRepository outbox) {
    DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
    bf.registerSingleton("workflowStatusOutboxRepository", outbox);
    return bf.getBeanProvider(WorkflowStatusOutboxRepository.class);
  }

  static ObjectProvider<WorkflowIdGenerator> idGeneratorProvider(WorkflowIdGenerator generator) {
    DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
    bf.registerSingleton("workflowIdGenerator", generator);
    return bf.getBeanProvider(WorkflowIdGenerator.class);
  }

  /** 空 provider：模拟 starter 端口缺失（宿主未接引擎基础设施），getIfAvailable 返回 null。 */
  static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
    return new DefaultListableBeanFactory().getBeanProvider(type);
  }
}
