package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.AssignPermissionCommand;
import com.bone.iam.application.command.CreateRoleCommand;
import com.bone.iam.application.command.UpdateRoleCommand;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.dto.RoleDetailDTO;
import com.bone.iam.application.query.qry.RolePageQuery;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.repository.RoleRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色应用层统一门面（Application Service First）——角色类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code application.command.handler.*RoleCommandHandler} 与 {@code
 * application.query.handler.RolePageQueryHandler / RolePermissionsQueryHandler /
 * RoleDetailQueryHandler} 已全量内联进本类（覆盖 E-3.11 的一次性大爆炸收敛）。适配器只依赖本类，HTTP 契约保持不变。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL（{@code com.bone.metadata.sdk.query.*}）与 {@code TenantContext}：分页 DSL 下沉 {@link
 * RoleRepository#findRolePage}，权限展开下沉 {@link RolePermissionRepository#findPermissionsOfRole}，租户取值走
 * {@link TenantProvider} 端口（E-2 / E-4.2）。
 */
/*
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（Role / RolePermission）当前不发布领域事件，其创建/更新/授权均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class RoleApplicationService {

  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final TenantQuotaEnforcer tenantQuotaEnforcer;
  private final TenantProvider tenantProvider;
  private final AccountAuthorityCache accountAuthorityCache;

  @Transactional
  public Long create(CreateRoleCommand cmd) {
    Long tenantId = resolveTenantId(cmd.getTenantId());
    tenantQuotaEnforcer.assertCanAddRole(tenantId);
    String code = cmd.getCode();
    if (code == null || code.isBlank()) {
      code =
          cmd.getName() == null ? "" : cmd.getName().trim().replaceAll("\\s+", "_").toUpperCase();
    }
    Role role = Role.create(cmd.getName(), code, cmd.getDescription(), 1, tenantId, null);
    roleRepository.save(role);
    return role.getId();
  }

  @Transactional
  public void update(UpdateRoleCommand cmd) {
    Role role = roleRepository.findById(cmd.getId());
    if (role == null) {
      throw IamErrors.of(IamErrorCodes.ROLE_NOT_FOUND, "角色不存在");
    }
    if (cmd.getDescription() != null) {
      role.update(cmd.getDescription());
    }
    roleRepository.update(role);
  }

  @Transactional
  public void delete(Long id) {
    roleRepository.deleteById(id);
  }

  @Transactional
  public void assignPermission(AssignPermissionCommand cmd) {
    if (cmd == null || cmd.getRoleId() == null) {
      throw IamErrors.of(IamErrorCodes.ROLE_ID_REQUIRED, "角色 ID 不能为空");
    }
    Role role = roleRepository.findById(cmd.getRoleId());
    if (role == null) {
      throw IamErrors.of(IamErrorCodes.ROLE_NOT_FOUND, cmd.getRoleId());
    }
    assertCallerMayManageRole(role);
    rolePermissionRepository.replaceBindingsForRole(cmd.getRoleId(), cmd.getPermissionIds());
    accountAuthorityCache.evictAccountsForRole(cmd.getRoleId());
  }

  /** 非平台租户（tenantId &gt; 0）只能操作本租户角色，防 IDOR。 */
  private void assertCallerMayManageRole(Role role) {
    Long callerTenant = tenantProvider.currentTenantIdOrNull();
    if (callerTenant != null && callerTenant != 0L && !callerTenant.equals(role.getTenantId())) {
      throw IamErrors.of(IamErrorCodes.TENANT_ACCESS_DENIED, "无权操作其他租户的角色");
    }
  }

  @Transactional(readOnly = true)
  public PageResult<RoleDTO> page(RolePageQuery qry) {
    Long tenantFilter = resolveTenantFilter(qry.getTenantId());
    PageResult<Role> result =
        roleRepository.findRolePage(qry.getKeyword(), tenantFilter, qry.getPage(), qry.getSize());
    List<RoleDTO> dtoList =
        result.getRecords().stream().map(RoleApplicationService::toDto).toList();
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  /** 角色详情（含已授予权限 id）；非平台租户不可查看其他租户的角色（防 IDOR，详设 §3.4 / §4.8）。 */
  @Transactional(readOnly = true)
  public Optional<RoleDetailDTO> detail(Long id) {
    return Optional.ofNullable(roleRepository.findById(id))
        .filter(this::visibleToCaller)
        .map(
            role -> {
              RoleDetailDTO dto = new RoleDetailDTO();
              dto.setId(role.getId());
              dto.setName(role.getName());
              dto.setCode(role.getCode());
              dto.setDescription(role.getDescription());
              dto.setTenantId(role.getTenantId());
              dto.setCreatedAt(role.getCreatedAt());
              dto.setUpdatedAt(role.getUpdatedAt());
              List<Long> permissionIds =
                  rolePermissionRepository.findPermissionsOfRole(id).stream()
                      .map(Permission::getId)
                      .toList();
              dto.setPermissionIds(permissionIds.toArray(Long[]::new));
              return dto;
            });
  }

  /** 某角色已授予的权限列表。 */
  @Transactional(readOnly = true)
  public List<PermissionDTO> permissions(Long roleId) {
    if (roleId == null) {
      return Collections.emptyList();
    }
    return rolePermissionRepository.findPermissionsOfRole(roleId).stream()
        .map(RoleApplicationService::toPermissionDto)
        .toList();
  }

  private boolean visibleToCaller(Role role) {
    Long caller = tenantProvider.currentTenantIdOrNull();
    return caller == null || caller == 0L || caller.equals(role.getTenantId());
  }

  private Long resolveTenantId(Long fromCommand) {
    if (fromCommand != null) {
      return fromCommand;
    }
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    return fromContext != null ? fromContext : 0L;
  }

  /**
   * 解析有效租户过滤（详设 §3.4 / §4.8）：
   *
   * <ul>
   *   <li>调用方非平台租户（&gt; 0）→ 强制按其过滤，忽略查询参数；
   *   <li>平台租户（0）或无租户上下文 → 回退到查询参数，未传则不加过滤。
   * </ul>
   */
  private Long resolveTenantFilter(Long fromQuery) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }

  private static RoleDTO toDto(Role role) {
    RoleDTO dto = new RoleDTO();
    dto.setId(role.getId());
    dto.setName(role.getName());
    dto.setCode(role.getCode());
    dto.setDescription(role.getDescription());
    dto.setTenantId(role.getTenantId());
    dto.setCreatedAt(role.getCreatedAt());
    dto.setUpdatedAt(role.getUpdatedAt());
    return dto;
  }

  private static PermissionDTO toPermissionDto(Permission p) {
    PermissionDTO dto = new PermissionDTO();
    dto.setId(p.getId());
    dto.setCode(p.getCode());
    dto.setName(p.getName());
    dto.setResourceType(p.getResourceType());
    dto.setResourcePath(p.getResourcePath());
    dto.setAction(p.getAction());
    return dto;
  }
}
