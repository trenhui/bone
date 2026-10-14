package com.bone.metadata.catalog.domain.gateway;

import java.util.Set;

/**
 * 界面 schema 引用完整性检查端口（UI-REF-02）。
 *
 * <p><b>为什么单独成端口</b>：{@code actionCode} 的真源是 {@code meta_entity_action_def}（runtime 界的读仓储 catalog
 * 不反向依赖，与 0040 建 {@code ActionDefMaterializePort} 时的边界理由一致）。字段存在性走 {@code
 * MetaFieldRepository}（同界聚合读模型），行策略走实体自带的 {@code rowPolicies} JSON——本端口只声明 catalog 侧缺的那一块能力，实现落在
 * {@code infrastructure/gateway}。
 */
public interface UiSchemaRefCheckPort {

  /**
   * 查询某实体在 {@code meta_entity_action_def} 下的全部动作码（含各状态位，未过滤发布态—— 「已发布
   * action」由调用方按实体发布态判定，此处只回答存在性）。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code
   * @return 动作码集合（空集合 = 该实体无动作定义）
   */
  Set<String> actionCodesOf(long tenantId, String entityCode);
}
