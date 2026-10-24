package com.bone.airag.domain.service;

import com.bone.airag.domain.gateway.RagAclPort;
import java.util.Set;

/**
 * 文档级 ACL 的<b>开发联调形态</b>（仅租户硬隔离）：返回 {@code null} 表示「租户内全部文档可见」。
 *
 * <p><b>2026-10-24 起不再是默认</b>（企业复杂场景诊断 P0-2）：默认已改为 fail-closed 的 {@link DenyDocAcl} （未接入 ACL
 * 模型即拒绝检索）。本类仅当显式配置 {@code bone.ai.rag.acl.default-deny=false} 时装配， 且装配时打 WARN——
 * 该形态存在同租户内跨文档越权面（单租户多部门 / 多团队场景），禁止带入生产。
 *
 * <p>接入方以 {@code @Bean} 覆盖为文档中心 ACL 实现（读文档中心 / bone-ai-document 权限）后，无 ACL 归属的 doc 即不可见（fail-closed
 * 403）。
 */
public class NoDocAcl implements RagAclPort {

  @Override
  public Set<String> visibleDocIds(Long tenantId) {
    return null;
  }
}
