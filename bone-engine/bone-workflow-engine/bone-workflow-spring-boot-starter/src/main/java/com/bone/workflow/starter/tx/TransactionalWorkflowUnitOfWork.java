package com.bone.workflow.starter.tx;

import com.bone.workflow.ports.spi.WorkflowUnitOfWork;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用平台事务管理器实现引擎的用例级事务边界。
 *
 * <p><b>为什么用 {@link TransactionTemplate} 而不是在引擎里打 {@code @Transactional}</b>：{@code
 * bone-workflow-core} 零 Spring 依赖（架构测试守着），且引擎要能被非 Spring 宿主复用。注解只能打在 Spring bean 的方法上， 打不到「core
 * 里的一个用例方法」——那样做等于把事务边界交给调用方，而调用方恰恰是最容易漏的那一层。
 *
 * <p><b>传播行为必须是 {@code REQUIRED}</b>：宿主可能已经在自己的事务里调引擎（例如「提交报销」在同一事务内 start 流程）。 开 {@code
 * REQUIRES_NEW} 会让内层先提交，外层再回滚时留下<b>已启动的流程实例</b>——单据没落库、流程却在跑， 比完全没有事务更难查。
 *
 * <p><b>回滚口径</b>：任何 {@link RuntimeException} 与 {@link Error} 都回滚（{@code TransactionTemplate}
 * 的默认行为）。受检异常在引擎里不存在——端口签名不声明受检异常，故不必额外配 {@code rollbackFor}。
 */
public class TransactionalWorkflowUnitOfWork implements WorkflowUnitOfWork {

  private final TransactionTemplate template;

  public TransactionalWorkflowUnitOfWork(PlatformTransactionManager transactionManager) {
    this(newTemplate(transactionManager));
  }

  TransactionalWorkflowUnitOfWork(TransactionTemplate template) {
    this.template = Objects.requireNonNull(template, "template");
  }

  private static TransactionTemplate newTemplate(PlatformTransactionManager transactionManager) {
    Objects.requireNonNull(transactionManager, "transactionManager");
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return template;
  }

  @Override
  public <T> T execute(Supplier<T> work) {
    return template.execute(status -> work.get());
  }
}
