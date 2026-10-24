package com.bone.airag.domain.service;

import com.bone.airag.domain.gateway.RagAclPort;
import java.util.Set;

/**
 * 默认文档级 ACL（fail-closed）：未接入文档级权限模型时<b>拒绝检索</b>，而非放行全租户。
 *
 * <p><b>为什么改默认（2026-10-24 企业复杂场景诊断 P0-2）</b>：原默认 {@link NoDocAcl} 返回 {@code null} = 「租户内
 * 全部文档可见」。企业真实形态是**单租户内多部门 / 多团队**（理赔、核赔、医学共享一个租户），兜底放行意味着任一能调 {@code /rag/retrieve}
 * 的服务都可跨文档读取——是真实数据泄露面，不是边界 case。业界知识库权限模型的共识是 <b>默认 deny（fail-closed）</b>：没有 ACL 模型就不该放行。
 *
 * <p>返回<b>空集合</b>（契约语义 = 全员不可见）：显式 {@code doc_scope} ⇒ 403 {@code AI_RAG_DOC_FORBIDDEN}； 无作用域 ⇒
 * 候选按可见集过滤后为空。接入方以 {@code @Bean} 覆盖为文档中心 ACL 实现后即恢复受限检索。
 *
 * <p>开发联调逃生口：{@code bone.ai.rag.acl.default-deny=false} 显式回落 {@link NoDocAcl}（仅租户硬隔离）， 装配时打
 * WARN——该形态存在同租户跨文档越权面，禁止带入生产。
 */
public class DenyDocAcl implements RagAclPort {

  @Override
  public Set<String> visibleDocIds(Long tenantId) {
    return Set.of();
  }
}
