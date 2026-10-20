package com.bone.metadata.runtime.domain;

import java.util.List;

/**
 * 当前请求角色码端口（字段级角色裁剪的身份来源）。
 *
 * <p><b>三态语义（与 engine 侧契约严格对齐）</b>：
 *
 * <ul>
 *   <li>{@code null}：无认证主体（内部/受信调用、dev 关闭安全时的匿名请求）——engine 不做字段级裁剪；
 *   <li>空集合：已认证但无任何角色（JWT 无 {@code roleCodes} claim 的服务账号等）——受限字段对其不可见（fail-closed）；
 *   <li>非空集合：按角色码交集判定。
 * </ul>
 *
 * 绝不在本端口把「无角色」折叠成 null——那会把 fail-closed 退化成 fail-open。
 */
public interface CurrentRoleProvider {

  /** 当前角色码；无认证主体返回 {@code null}（绝不返回空集合冒充「已认证无角色」）。 */
  List<String> currentRoleCodesOrNull();
}
