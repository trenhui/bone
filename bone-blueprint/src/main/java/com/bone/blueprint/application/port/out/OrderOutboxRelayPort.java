package com.bone.blueprint.application.port.out;

/**
 * Outbox 中继端口（技术出站端口，E-4.3 / E-10.2）。
 *
 * <p>Outbox 中继扫描 PENDING 记录投递 MQ，本质是基础设施<strong>技术动作</strong>，属应用流程需要的技术能力，声明于 {@code
 * application/port/out}，实现于 infrastructure；不进 {@code domain/gateway}。将其抽象为端口，使 application 的协调层 与
 * adapter 的定时任务都只依赖端口接口，而非 infrastructure 实现类（E-3 R1：依赖向内）。
 */
public interface OrderOutboxRelayPort {

  /** 扫描并中继所有 PENDING 的 Outbox 记录，返回成功投递条数。 */
  int relayPending();
}
