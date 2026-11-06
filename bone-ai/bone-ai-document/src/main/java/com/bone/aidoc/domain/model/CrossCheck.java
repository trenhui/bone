package com.bone.aidoc.domain.model;

/**
 * L3 多模型交叉校验结论（ADR-0046 §四：多模型一致 ≠ 正确，但<b>不一致必须升级</b>）。
 *
 * <ul>
 *   <li>{@code MATCH}：主/次引擎对同一字段给出一致值（仅作为辅助信号，不单独决定自动通过）。
 *   <li>{@code MISMATCH}：两者给出不同值——直接升级人工复核，不得自动放行（防共同幻觉 + 防分歧）。
 *   <li>{@code NOT_RUN}：未配置次引擎，或该字段仅主引擎识别到（无共识基准）。
 * </ul>
 */
public enum CrossCheck {
  MATCH,
  MISMATCH,
  NOT_RUN
}
