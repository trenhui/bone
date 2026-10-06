/**
 * 定时任务包（adapter/schedule）——5 个 Job 共享的部署与调度约束。
 *
 * <p><b>单实例假设（P0-2 显式契约）</b>：本包全部 {@code @Scheduled} 任务按「生产单实例部署」设计， 未加分布式互斥（ShedLock / DB
 * 锁）。多实例部署前必须先补互斥，否则超时取消、库存广播、 Outbox 中继会出现重复触发（多数已被幂等兜底，但对账与日志会失真）。见模块 README「调度任务部署假设」。
 *
 * <p><b>固定延迟优先</b>：防重扫类任务一律 {@code fixedDelay}（上一轮未完成不叠加下一轮）， 需要对齐整点窗口的用 cron。技术出站端口直注（E-5.3
 * 禁纯技术轮询中转层）。
 *
 * <p>全租户扫描的受控例外与门禁（{@code all_tenants_scan_only_by_schedule} 等）见各 Job 类注释与 ADR-0030。
 */
package com.bone.blueprint.adapter.schedule;
