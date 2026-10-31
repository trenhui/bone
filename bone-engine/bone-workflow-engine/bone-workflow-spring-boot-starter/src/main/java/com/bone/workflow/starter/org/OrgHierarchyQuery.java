package com.bone.workflow.starter.org;

import java.util.Optional;

/**
 * 组织层级取数口（一个账户 → 它的组织位置）。
 *
 * <p>抽成独立接口是为了把「怎么取」和「取不到怎么办」分开：
 *
 * <ul>
 *   <li>本接口的实现只管发请求、解析响应；失败<b>抛异常</b>（不要吞成 empty）——否则「IAM 挂了」与 「这个人真的没有主管」会被混为一谈，运维永远看不到前者。
 *   <li>{@link IamOrgHierarchyProvider} 负责把异常统一收敛成 empty（fail-closed）并记日志。
 * </ul>
 *
 * <p>单测由此可以传一个 lambda 完整覆盖缓存、向上 N 级、环保护等逻辑，不需要起 HTTP 服务。
 */
@FunctionalInterface
public interface OrgHierarchyQuery {

  /**
   * 查询某账户的组织位置。
   *
   * @param accountId 账户 id（字符串，雪花 id 超出 JS 安全整数）
   * @return 查不到该账户时返回 {@link Optional#empty()}；请求失败应<b>抛异常</b>而不是返回 empty
   */
  Optional<OrgHierarchy> byAccountId(String accountId);
}
