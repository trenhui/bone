package com.bone.airag.domain.gateway;

import java.util.Set;

/**
 * 文档级可见性端口（详设 §2.6 检索链第一环：租户 / 权限过滤；2026-10-23 RAG 诊断 P1-7 落地）。
 *
 * <p><b>为何是端口而非 rag 自建 ACL 表</b>：文档权限模型归属文档中心 / bone-ai-document（首批内部试点租户内
 * 文档权限模型未定），rag 只消费「可见 doc_id 集合」这一契约，不重复建设权限存储。接入方以 {@code @Bean}
 * 提供本端口实现（如读文档中心 ACL），未提供时走默认 {@link com.bone.airag.domain.service.NoDocAcl}
 * （仅租户硬隔离兜底）。
 *
 * <p>契约语义（fail-closed）：
 * <ul>
 *   <li>返回 {@code null}——无文档级 ACL 模型（默认兜底）：仅租户硬隔离，租户内全部文档可见；</li>
 *   <li>返回非 {@code null}——受限集合：<b>无 ACL 归属的 doc 对当前调用方不可见</b>。显式 {@code docScope}
 *       不在集合内 ⇒ 检索抛 {@code AI_RAG_DOC_FORBIDDEN}（403，绝不静默回落全租户检索）；无显式作用域时
 *       按集合过滤候选切片。</li>
 * </ul>
 *
 * <p>调用方身份由实现自行从上下文解析（与 {@link TenantProvider} 同构），本端口不接收 user 入参——避免
 * 「调用方伪造身份参数」绕权（租户 / 身份是安全边界，不是业务参数）。
 */
public interface RagAclPort {

  /**
   * 当前调用方在指定租户内可见的 doc_id 集合。
   *
   * @return {@code null} = 无文档级 ACL 模型（仅租户硬隔离）；非 {@code null} = 受限可见集合（空集合 = 全员不可见）
   */
  Set<String> visibleDocIds(Long tenantId);
}
