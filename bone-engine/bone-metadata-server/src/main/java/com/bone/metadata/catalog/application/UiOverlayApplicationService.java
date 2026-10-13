package com.bone.metadata.catalog.application;

import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.gateway.UiOverlayStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 界面列投影覆盖的应用服务（迁移 0042 · P2-④）。
 *
 * <p>入站边界语义化（ADR-0028）：控制器不直接碰端口。租户取自 {@link TenantProvider}（JWT tenantId → 网关 X-Tenant-Id 链路），用户取自
 * {@link CurrentUserProvider}（JWT userId claim）。
 *
 * <p><b>无主体场景</b>：API-Key / E2E 直连无 userId claim → 落 {@code 0}（anon Scope）。 这与前端「未登录退化到
 * 0.anon」同口径——两端对「无主体」的键定义一致，才不会出现 「服务端写在 anon 行、前端从 bone-user 行读」的串键。
 */
@Service
public class UiOverlayApplicationService {

  private final UiOverlayStore uiOverlayStore;
  private final TenantProvider tenantProvider;
  private final CurrentUserProvider currentUserProvider;

  public UiOverlayApplicationService(
      UiOverlayStore uiOverlayStore,
      TenantProvider tenantProvider,
      CurrentUserProvider currentUserProvider) {
    this.uiOverlayStore = uiOverlayStore;
    this.tenantProvider = tenantProvider;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * 读当前用户在当前租户下的实体覆盖 JSON。
   *
   * @return 覆盖 JSON；从未配置或已 reset 时 {@code null}
   */
  @Transactional(readOnly = true)
  public String getOverlayJson(String entityCode) {
    return store()
        .findOverlayJson(tenantId(), userId(), requireEntityCode(entityCode))
        .orElse(null);
  }

  /** 保存覆盖（UPSERT，幂等）。 */
  @Transactional
  public void saveOverlayJson(String entityCode, String overlayJson) {
    store().saveOverlayJson(tenantId(), userId(), requireEntityCode(entityCode), overlayJson);
  }

  /** 重置为默认投影（软删）。 */
  @Transactional
  public void clearOverlay(String entityCode) {
    store().clearOverlay(tenantId(), userId(), requireEntityCode(entityCode));
  }

  private UiOverlayStore store() {
    return uiOverlayStore;
  }

  private long tenantId() {
    return tenantProvider.currentTenantId();
  }

  private long userId() {
    Long uid = currentUserProvider.currentUserIdOrNull();
    return uid == null ? 0L : uid;
  }

  private static String requireEntityCode(String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      throw new DomainException("实体编码不能为空");
    }
    return entityCode;
  }
}
