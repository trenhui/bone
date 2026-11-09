package com.bone.claim.domain.model.liability;

/**
 * 责任规则读侧模式（M2 ③ 3a）。
 *
 * <p><b>为什么是领域枚举而不是 boolean</b>：{@code true/false} 无法表达"以后可能出现第三种模式"， 而
 * 3b（读侧切主）天然就是第三种取值。用枚举让调用点在编译期就必须显式声明语义，避免 "开关打开了但不知道该走哪条路"这种含糊状态被一个布尔值掩盖过去。
 *
 * <p><b>两种模式都必须零行为变更</b>：{@link #DUAL_READ} 也只是"多算一条路径并比对"， 返回给调用方的金额始终来自旧标量路径——真正的切主属于后续独立 change。
 */
public enum LiabilityReadMode {

  /** 只走旧标量列：与 M2 ② 落地后的行为逐笔一致，且不解析聚合 JSON。 */
  LEGACY_ONLY,

  /** 双轨：同时计算旧标量路径与按键回落路径，差异只记录，返回旧路径结果。 */
  DUAL_READ
}
