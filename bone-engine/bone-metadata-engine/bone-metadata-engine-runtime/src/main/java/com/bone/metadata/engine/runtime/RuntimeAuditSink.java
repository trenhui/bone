package com.bone.metadata.engine.runtime;

/**
 * 记录级变更审计落库出口（RC-009）。
 *
 * <p>engine 侧只依赖本函数式接口，不感知具体审计表与 ORM；宿主应用（{@code bone-metadata-server}） 注入 JDBC 实现。默认 {@link #NOOP}
 * 保证 engine 在无宿主实现时仍可用（测试 / 独立运行）。
 */
@FunctionalInterface
public interface RuntimeAuditSink {

  RuntimeAuditSink NOOP = entry -> {};

  void record(RuntimeAuditEntry entry);
}
