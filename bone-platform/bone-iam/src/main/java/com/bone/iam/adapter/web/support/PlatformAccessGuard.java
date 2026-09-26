package com.bone.iam.adapter.web.support;

import com.bone.iam.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/**
 * 平台域接口的**访问侧**护栏（纵深防御第二道）。
 *
 * <p>为什么需要它：平台域资源（租户 / 权限目录 / 全局会话）作用于平台级数据，但它们的 {@code hasAuthority} 只校验权限码、不校验调用方租户身份——只要某租户成员拿到了
 * {@code iam:tenants:read} 这个码，就能读全量租户。绑定侧已有拦截 （{@code
 * RoleApplicationService}），但那只是**授权时**的校验：存量脏绑定、后台直接改库、平台管理员误把平台码绑到租户角色等情况都会绕过它。
 *
 * <p>业界做法（AWS IAM / Entra ID 的 policy 在每次 API 调用时求值，而非只在授权时求值）要求**在资源访问点做判定**。故平台域 Controller 的
 * {@code @PreAuthorize} 一律追加 {@code and @platformAccessGuard.isPlatformAdmin()}：权限码回答「你能做什么」，
 * 本护栏回答「你以什么身份做」，两者缺一不可。
 *
 * <p>fail-closed：租户上下文缺失（{@code null}）视为不通过——未认证/上下文未写入的请求不应触达平台域数据。
 */
@Component("platformAccessGuard")
public class PlatformAccessGuard {

  private final TenantProvider tenantProvider;

  public PlatformAccessGuard(TenantProvider tenantProvider) {
    this.tenantProvider = tenantProvider;
  }

  /** 仅平台租户（tenantId = 0）可访问平台域接口；租户用户与无上下文请求一律 false。 */
  public boolean isPlatformAdmin() {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    return tenantId != null && tenantId == 0L;
  }
}
