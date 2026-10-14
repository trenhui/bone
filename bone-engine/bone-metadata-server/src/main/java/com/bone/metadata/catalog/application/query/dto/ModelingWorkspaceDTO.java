package com.bone.metadata.catalog.application.query.dto;

import java.util.List;

/**
 * 建模工作台（模型设计页）聚合读模型（P0-1：消除首屏 N+5 次串行请求）。
 *
 * <p>一次返回：草稿/总数/已发布三项统计 + 待发布草稿前 6 条 + 应用→模块两级结构。 页面原有的取数路径是 {@code listMine×2 + listByApp×N +
 * entities page×3（串行）}， N 随应用数线性增长；本 DTO 将其收敛为一次 RTT （服务端 6 条同库查询）。
 *
 * <p>应用列表本体（含 myRole / icon）仍走 IAM {@code /apps/mine}——那是对「当前用户可见应用」的 认证侧投影，与本端点的 「建模统计 +
 * 模块结构」关注点不同，且无需 metadata 读权限即可加载， 故不合并（合并会把页面整体锁死到 {@code metadata:model:read}，改变 runtime-only
 * 用户的现有行为）。
 *
 * @param entityTotal 租户实体总数（不分状态）
 * @param draftTotal 草稿数（待发布）
 * @param publishedTotal 已发布数
 * @param recentDrafts 待发布草稿前 6 条（按 id 倒序，与业务对象页同口径）
 * @param appModules 应用→模块两级结构（仅建模消费的 id/name/code 最小集）
 */
public record ModelingWorkspaceDTO(
    long entityTotal,
    long draftTotal,
    long publishedTotal,
    List<MetaEntityDTO> recentDrafts,
    List<AppModulesDTO> appModules) {

  /** 应用→模块分组（应用名仅作分组标签；应用聚合真源在 IAM）。 */
  public record AppModulesDTO(long appId, String appName, List<ModuleItemDTO> modules) {}

  /** 模块最小读模型（目标选择器下拉 + moduleId→appId 反查共用）。 */
  public record ModuleItemDTO(long id, String name, String code) {}
}
