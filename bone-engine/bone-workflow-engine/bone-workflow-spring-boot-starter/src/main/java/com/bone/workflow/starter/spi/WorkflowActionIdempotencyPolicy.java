package com.bone.workflow.starter.spi;

/**
 * 动作幂等策略端口（P0-2 中继幂等硬约束）。
 *
 * <p>状态投影中继是 <b>at-least-once</b> 投递：进程在「执行业务动作」与「标记 DONE」之间崩溃，
 * 该行会在超时后被其它副本重新抢占并<b>再次执行</b>同一业务动作。若目标动作本身不幂等，重投 = 业务状态被推进两次（重复批单 / 重复驳回）。
 *
 * <p>本端口由宿主提供：声明某动作是否「可安全在 at-least-once 中继下重投」。中继在执行前查询它——
 *
 * <ul>
 *   <li>返回 {@code true}（声明幂等，如 submit / approve / reject / withdraw 四个逃逸动作，宿主在 {@code
 *       meta_model_template_action.requires_idempotency=1} 标注）→ 照常执行；
 *   <li>返回 {@code false}（未声明幂等）→ 中继<b>拒绝执行并落终态 FAILED</b>（fail-closed），而非冒险双推进。
 * </ul>
 *
 * <p><b>opt-in 与默认放行</b>：宿主未装配本端口时（老部署 / 尚未接入），中继打 WARN 并<b>放行</b>（不阻断既有链路）， 但此时 P0-2
 * 硬约束未生效——宿主应尽早提供此端口以点亮该护栏。装配本端口即代表宿主已对其动作幂等性负责。
 */
public interface WorkflowActionIdempotencyPolicy {

  /**
   * 该动作是否声明幂等（可安全在 at-least-once 中继下重复执行而不产生副作用放大）。
   *
   * @param entityCode 业务实体编码
   * @param tenantId 租户 id
   * @param actionCode 平台动作码（如 {@code approve} / {@code reject}）
   * @return {@code true}=已声明幂等、可安全重投；{@code false}=未声明、中继须拒绝
   */
  boolean requiresIdempotency(String entityCode, long tenantId, String actionCode);
}
