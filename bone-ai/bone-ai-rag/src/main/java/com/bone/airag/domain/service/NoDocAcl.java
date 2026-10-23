package com.bone.airag.domain.service;

import com.bone.airag.domain.gateway.RagAclPort;
import java.util.Set;

/**
 * 默认文档级 ACL：无文档级权限模型（仅租户硬隔离兜底，首批内部试点形态）。
 *
 * <p>返回 {@code null} 表示「租户内全部文档可见」——检索链按租户隔离执行。接入方以 {@code @Bean} 覆盖为
 * 本端口真实实现（读文档中心 / bone-ai-document ACL）后，无 ACL 归属的 doc 即不可见（fail-closed）。
 * L3 私有化交付前必须闭环该越权面（详设 §2.6 P1 backlog 转生产项）。
 */
public class NoDocAcl implements RagAclPort {

  @Override
  public Set<String> visibleDocIds(Long tenantId) {
    return null;
  }
}
