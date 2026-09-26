package com.bone.metadata.catalog.domain.service;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.iam.IamAppRoleRef;
import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.catalog.domain.repository.IamAppRoleRepository;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 模块存在性、归属与应用角色校验器（下游/Supplier 关系中的 Customer 侧守卫）。
 *
 * <p>metadata 上下文不再拥有模块聚合根，实体(Entity)/字段(Field)必须挂在 IAM 上下文的 {@code bone_module} 之下。本校验器在 metadata
 * 写入实体/字段前确认所引用的 {@code moduleId} 在 IAM 的 {@code bone_module} 中真实存在，维持跨上下文引用一致性。
 *
 * <p><b>租户一致性必须显式断言</b>：SDK 读侧会自动注入 {@code tenant_id} 过滤（ADR-0029），因此跨租户的 moduleId 今日会被
 * 隐式挡成「模块不存在」。但该防线是<b>隐式</b>的——一旦有人绕过自动过滤（改用 disableTenantFilter 或改走 Feign），防线即失效且无人察觉。
 * 故在此显式比对租户并给出明确错误，把「隐式碰巧挡住」变成「显式契约」。
 *
 * <p><b>应用角色校验（G1②，2a §4.2 / §10.3）</b>：同租户内应用是建模权限边界——建模者须持有目标应用的 {@code ADMIN} 或 {@code
 * DEVELOPER} 角色（读 {@code bone_app_permission}）。无主体场景（E2E 安全关闭 / API-Key / 定时任务）按「平台运维通道」
 * <b>豁免</b>但记 WARN——豁免裁定见 2a §10.4；服务级凭证的权限已在认证层收敛（SecurityConfig authorities）。
 */
@Slf4j
@RequiredArgsConstructor
public class IamModuleValidator {

  /** 允许建模的应用角色（大写存储值，与 IAM {@code AppRole.name()} 同口径）。 */
  private static final Set<String> MODELING_ROLES = Set.of("ADMIN", "DEVELOPER");

  private final IamModuleRepository iamModuleRepository;
  private final IamAppRoleRepository iamAppRoleRepository;
  private final TenantProvider tenantProvider;

  /** 若指定 moduleId 在 IAM 模块中不存在（或不属于当前租户），抛出异常。 */
  public void requireExists(Long moduleId) {
    requireModule(moduleId);
  }

  /**
   * 建模准入校验（G1②）：模块存在 + 租户一致 + 当前用户对模块所属应用持有建模角色。
   *
   * @param moduleId 目标模块
   * @param userId 当前登录用户账号 ID；{@code null} 表示无主体场景（豁免角色校验，记 WARN）
   */
  public void requireModelingAllowed(Long moduleId, Long userId) {
    IamModuleRef ref = requireModule(moduleId);
    if (userId == null) {
      log.warn(
          "[G1②] 无主体建模（豁免应用角色校验）：moduleId={} tenant={} —— 若非 E2E/API-Key/系统任务，请检查认证配置",
          moduleId,
          tenantProvider.currentTenantId());
      return;
    }
    IamAppRoleRef role = iamAppRoleRepository.findByAppAndUser(ref.getAppId(), userId).orElse(null);
    if (role == null || !MODELING_ROLES.contains(role.getRole())) {
      throw BizException.of(
          403,
          "无该应用下的建模权限：需要目标应用（appId="
              + ref.getAppId()
              + "）的 ADMIN 或 DEVELOPER 角色"
              + (role == null ? "，当前用户未被授予任何应用角色" : "，当前角色=" + role.getRole()));
    }
  }

  private IamModuleRef requireModule(Long moduleId) {
    if (moduleId == null) {
      throw BizException.of("模块ID不能为空");
    }
    IamModuleRef ref = iamModuleRepository.findById(moduleId);
    if (ref == null) {
      throw BizException.of("所属模块不存在: " + moduleId);
    }
    assertSameTenant(moduleId, ref);
    return ref;
  }

  private void assertSameTenant(Long moduleId, IamModuleRef ref) {
    long currentTenantId = tenantProvider.currentTenantId();
    // DDL 保证 bone_module.tenant_id NOT NULL，故 null 只可能来自「读到的不是真实行」（如单测桩）→ 一并拒绝。
    if (ref.getTenantId() == null || ref.getTenantId().longValue() != currentTenantId) {
      throw BizException.of(
          "所属模块不属于当前租户: moduleId="
              + moduleId
              + "（模块租户="
              + ref.getTenantId()
              + "，当前租户="
              + currentTenantId
              + "）");
    }
  }
}
