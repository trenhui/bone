package com.bone.blueprint.infrastructure.messaging.outbox;

/**
 * Outbox 投递状态（<strong>技术状态机</strong>，非业务状态）。
 *
 * <p>与 DDL {@code bp_outbox.status} 列的 CHECK 约束严格对齐（bone-init.sql 为唯一真理源）。
 *
 * <h3>状态机</h3>
 *
 * <pre>
 * PENDING ──(UPDATE CAS 抢占)──▶ PROCESSING ──(发 MQ 成功)──▶ SENT
 *                                │
 *                                └──(发 MQ 异常)──▶ FAILED（可由补偿重试）
 * </pre>
 *
 * <p><b>为何要 PROCESSING 中间态</b>：
 *
 * <ul>
 *   <li><strong>多实例抢占确定性</strong>：UPDATE CAS（WHERE status=PENDING → PROCESSING）是原子抢占， 比 FOR UPDATE
 *       SKIP LOCKED 跨数据库更通用（DM/Oracle/SQLServer 不支持 SKIP LOCKED）；
 *   <li><strong>先发后更窗口消除</strong>：PROCESSING 状态的记录不会被下一轮 Relay 重复抢占， 发 MQ 成功后 update 为 SENT（WHERE
 *       status=PROCESSING 保证 CAS），不会双投；
 *   <li><strong>故障恢复</strong>：PROCESSING 长时间未转为 SENT/FAILED 说明 Relay 崩溃，可由对账扫描重试。
 * </ul>
 */
public enum OutboxStatus {
  PENDING,
  PROCESSING,
  SENT,
  FAILED
}
