package com.bone.blueprint.domain.gateway;

/**
 * Outbox 中继端口（E-10 出站端口声明于 domain）。
 *
 * <p>Outbox 中继扫描 PENDING 记录投递 MQ，本质是基础设施技术动作；将其抽象为端口，使 application 的协调层 与 adapter 的定时任务都只依赖 domain
 * 端口，而非 infrastructure 实现类（E-3 R1：依赖向内）。
 */
public interface OrderOutboxRelayPort {

  /** 扫描并中继所有 PENDING 的 Outbox 记录，返回成功投递条数。 */
  int relayPending();
}
