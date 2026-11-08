package com.bone.claim.application.port.out;

import java.util.Set;

/**
 * 流程配置出站端口：本次决策图允许跳过的节点名集合。
 *
 * <p><b>契约</b>：永不返回 {@code null}、永不抛出；无配置 / 非法 JSON / 读失败一律返回空集 （= 按 canonical 图原样执行）。与 {@link
 * TriageLimitsPort} 同款「读失败回落基线」。
 *
 * <p><b>方向</b>：跳过是放宽，故回落方向必须是「不跳过」。配置生效的语义由消费方裁定—— 本仓第一阶段是「跳过 AI 评估即转人工」（fail-closed），不是自动通过。
 */
@FunctionalInterface
public interface DecisionFlowConfigPort {

  /** M5 第一阶段唯一流程键；与 DDL 注释中的 ADJUSTMENT_ORDER 示例值区分。运营改键须同步写入端。 */
  String FLOW_KEY = "DECISION_GRAPH";

  Set<String> skippableNodes(long tenantId);
}
