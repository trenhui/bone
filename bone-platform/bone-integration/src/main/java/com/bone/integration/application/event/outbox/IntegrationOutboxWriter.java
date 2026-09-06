package com.bone.integration.application.event.outbox;

import com.bone.core.domain.DomainEvent;

/**
 * Outbox（发件箱）写入端口——<strong>应用层定义，基础设施层实现</strong>。
 *
 * <p><b>为何端口在 application 而非 domain</b>：Outbox 解决的是「业务事务与消息投递之间的一致性」， 属应用层的
 * 集成职责；领域层不应感知消息中间件的存在。实现见 {@code infrastructure/messaging/outbox/IntegrationOutboxWriterImpl}。
 *
 * <p><b>为何实现在 infrastructure 而非 domain</b>：Outbox 记录只有 PENDING/SENT/FAILED 的<strong>技术
 * 状态</strong>，无业务不变量。放领域层会把技术设施概念混入业务领域（原 {@code domain/outbox} 已迁移）。
 *
 * <p><b>调用约束（关键）</b>：必须在业务写事务<strong>内</strong>调用，保证「业务状态变更」与「事件待发」 原子提交。若挪到
 * AFTER_COMMIT，业务已提交而事件未落库，宕机即丢事件——Outbox 模式将完全失效。
 */
public interface IntegrationOutboxWriter {

  /** 在业务事务内登记一条待发记录（事件类型须在 {@link IntegrationEventCatalog} 登记，否则仅记 warn 跳过）。 */
  void append(DomainEvent event);
}
