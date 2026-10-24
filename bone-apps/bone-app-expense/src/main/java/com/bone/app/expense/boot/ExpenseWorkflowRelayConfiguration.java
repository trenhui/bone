package com.bone.app.expense.boot;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 调度：审批流状态投影中继（{@code StatusProjectionRelay.runScheduled}）依赖 {@code @Scheduled} 基础设施。
 *
 * <p>不开启时 {@code @Scheduled} 被静默忽略（引擎 README「接入方式」已声明），发件箱意图无人落地—— 挂起/终态留痕会永久堆积。经核实 bone-boot
 * 组装面（metadata-server + 报销/会计/采购业务模块）内<b>没有其它</b> {@code @Scheduled} bean，开启调度只会激活本中继， 无附带效应。
 */
@Configuration
@EnableScheduling
public class ExpenseWorkflowRelayConfiguration {}
