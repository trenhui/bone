package com.bone.procurement.contract.application;

/**
 * AI 参与披露视图（只读）。
 *
 * <p>{@code aiAvailableCode} 是后端原始码，前端对未知态原样保留并显示「无法确认」。 {@code canConfirm} 只由参与态决定， {@code
 * modelVersionSnapshot} 仅作版本快照展示，<b>不作为参与证据</b>。
 */
public record AiDisclosureView(
    String reviewId,
    String aiAvailableCode,
    String disclosureText,
    boolean canConfirm,
    String modelVersionSnapshot) {}
