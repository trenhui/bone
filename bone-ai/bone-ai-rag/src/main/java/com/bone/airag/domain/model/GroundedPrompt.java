package com.bone.airag.domain.model;

import java.util.List;

/**
 * 组装完成的接地提示词（RAG→生成的核心产物）。
 *
 * <p>{@code included} 是<b>真正进入提示词的上下文</b>（可能因 {@code tokenBudget} 被截断），其顺序即引用编号 1..n。
 * 生成后的接地校验必须用 {@code included} 而非「全部检索结果」——否则会出现「模型引用了 [3]，但那一条根本没进
 * 提示词」的错位，守卫与提示词对不上账，校验结果无意义。
 *
 * @param system 系统提示（角色约束 + 强制引用规则 + 不可编造）
 * @param user 用户提示（数据围栏包裹的编号上下文 + 原始问题）
 * @param included 实际进入提示词的上下文（编号即列表下标 + 1）
 * @param estimatedTokens 上下文部分的 token 估算值（近似口径，见 {@code GroundedPromptBuilder}）
 * @param truncated 是否因预算发生了截断（true 时调用方应把 degradations 透出，让「答不全」可归因）
 */
public record GroundedPrompt(
    String system,
    String user,
    List<RagResult> included,
    int estimatedTokens,
    boolean truncated) {}
