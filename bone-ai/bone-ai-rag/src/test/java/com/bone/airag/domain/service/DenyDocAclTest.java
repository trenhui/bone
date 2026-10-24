package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 默认拒绝型文档级 ACL 单测（2026-10-24 企业复杂场景诊断 P0-2）。
 *
 * <p>契约只有一条但必须锁死：{@code visibleDocIds} 返回<b>空集合</b>而非 {@code null}——空集合的契约语义是 「全员不可见」（显式 doc_scope
 * ⇒ 403；无作用域 ⇒ 候选被过滤空），而 {@code null} 的语义是「无 ACL 模型 ⇒ 仅租户
 * 硬隔离、租户内全部可见」。两者一字之差，正是「兜底放行即跨文档越权面」与「默认拒绝」的分界。
 */
class DenyDocAclTest {

  private final DenyDocAcl acl = new DenyDocAcl();

  @Test
  void visible_doc_ids_is_empty_not_null() {
    // 空集合 = 全员不可见（fail-closed）；null 会被检索链解释为「无 ACL 模型 ⇒ 放行全租户」
    assertThat(acl.visibleDocIds(42L)).isNotNull().isEmpty();
  }
}
