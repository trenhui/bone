package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.gateway.UiOverlayStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 界面列投影覆盖应用服务单测（0042 · P2-④）。
 *
 * <p>覆盖三条契约：① 租户/用户键正确传导；② 无主体场景 user_id 落 0（anon Scope，与前端同口径）； ③ 空实体编码 fail-fast（防止把覆盖写到空键行）。
 */
class UiOverlayApplicationServiceTest {

  private final UiOverlayStore store = mock(UiOverlayStore.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);

  private final UiOverlayApplicationService service =
      new UiOverlayApplicationService(store, tenantProvider, currentUserProvider);

  @Test
  @DisplayName("getOverlayJson：按 (tenantId, userId, entityCode) 精确查键，未配置返回 null")
  void getOverlayJson_shouldQueryByTenantUserEntityKey() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    when(currentUserProvider.currentUserIdOrNull()).thenReturn(42L);
    when(store.findOverlayJson(7L, 42L, "exp_claim")).thenReturn(Optional.of("{\"version\":2}"));

    assertThat(service.getOverlayJson("exp_claim")).isEqualTo("{\"version\":2}");

    when(store.findOverlayJson(7L, 42L, "exp_claim")).thenReturn(Optional.empty());
    assertThat(service.getOverlayJson("exp_claim")).isNull();
  }

  @Test
  @DisplayName("无主体（API-Key/E2E 直连）落 user_id=0，不抛异常也不串写真实用户")
  void anonymousPrincipal_shouldFallToUserIdZero() {
    when(tenantProvider.currentTenantId()).thenReturn(0L);
    when(currentUserProvider.currentUserIdOrNull()).thenReturn(null);
    when(store.findOverlayJson(0L, 0L, "exp_claim")).thenReturn(Optional.of("{\"version\":2}"));

    assertThat(service.getOverlayJson("exp_claim")).isEqualTo("{\"version\":2}");
    verify(store).findOverlayJson(eq(0L), eq(0L), eq("exp_claim"));
  }

  @Test
  @DisplayName("空实体编码 fail-fast：防止把覆盖写到空键行污染唯一键空间")
  void blankEntityCode_shouldReject() {
    assertThatThrownBy(() -> service.getOverlayJson("  "))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("实体编码不能为空");
    assertThatThrownBy(() -> service.saveOverlayJson(null, "{}"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> service.clearOverlay("")).isInstanceOf(DomainException.class);
  }
}
