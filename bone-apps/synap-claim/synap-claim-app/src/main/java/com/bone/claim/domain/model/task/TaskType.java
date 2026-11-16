package com.bone.claim.domain.model.task;

/**
 * 任务类型（一案多任务的区分维度之一）。
 *
 * <p><b>为什么类型进唯一键</b>：{@code active_key = claim_id:task_type} 表达的是「同案同类型至多一条生效任务」。
 * 若把补件与初审合并成一个类型，两条业务上并行的任务就会互相挡住（后建的那条插不进去）， 于是要么报错、要么把前一条置为终态——两种都在丢事实。
 */
public enum TaskType {
  /** 初审（收单后进入待理算池的第一次人工处理）。 */
  INITIAL_REVIEW,
  /** 补件处理（材料不齐后由调查人补齐再送审）。 */
  FIX_SUPPLEMENT,
  /** 复审（双人复核的第二人）。 */
  SECOND_REVIEW,
  /** 后督抽检。 */
  INSPECTION
}
