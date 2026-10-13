package com.bone.metadata.catalog.domain.gateway;

import java.util.Optional;

/**
 * 界面列投影覆盖的用户级存储端口（迁移 0042 · P2-④ 服务端化）。
 *
 * <p><b>为什么服务端化</b>：PRD v0.32 §12.2 D2 定案 MVP 存 localStorage，遗留两个问题—— ① 换设备/清缓存即丢失（列偏好是账号级资产，参考
 * Jira / 飞书表格的服务端列偏好）； ② 同一账号多端无法同步。localStorage 键保留为<b>离线缓存</b>，hydrate 策略 「服务端优先 → 本地兜底 →
 * 默认投影」，投影纯函数（projectColumns）不变。
 *
 * <p><b>键模型</b>：{@code (tenant_id, user_id, entity_code)}——租户隔离 + 账号隔离 + 实体隔离。 无主体场景（API-Key / E2E
 * 直连）user_id 落 {@code 0}，与前端 anon Scope 同口径，不串写真实用户。
 */
public interface UiOverlayStore {

  /**
   * 查某用户在某实体下的覆盖 JSON（UiOverlay 序列化）。
   *
   * @return 覆盖 JSON；无记录或已 reset 时 {@link Optional#empty()}
   */
  Optional<String> findOverlayJson(long tenantId, long userId, String entityCode);

  /**
   * 保存覆盖（UPSERT）。
   *
   * <p>以 {@code uk_overlay_tenant_user_entity} 为准：重复保存幂等； reset（软删）后的首次保存通过 {@code deleted = 0}
   * 复活同一行，不撞唯一键。
   *
   * @param overlayJson UiOverlay 序列化（含 version 字段；版本校验在前端）
   */
  void saveOverlayJson(long tenantId, long userId, String entityCode, String overlayJson);

  /**
   * 重置为默认投影（软删）。
   *
   * <p>软删而非物理删：与平台逻辑删除同口径；唯一键不含 deleted，reset 后重建不撞键。
   */
  void clearOverlay(long tenantId, long userId, String entityCode);
}
