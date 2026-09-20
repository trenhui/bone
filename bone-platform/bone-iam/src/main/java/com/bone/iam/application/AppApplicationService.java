package com.bone.iam.application;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreateApplicationCommand;
import com.bone.iam.application.command.cmd.GrantAppPermissionCommand;
import com.bone.iam.application.command.cmd.UpdateApplicationCommand;
import com.bone.iam.application.query.dto.AppPermissionDTO;
import com.bone.iam.application.query.dto.ApplicationDTO;
import com.bone.iam.application.query.qry.ApplicationPageQuery;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.app.AppPermission;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.iam.domain.app.vo.AppRole;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AppPermissionRepository;
import com.bone.iam.domain.repository.BoneApplicationRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用（应用 / 应用内权限绑定）应用层统一门面（Application Service First）——应用类用例的唯一入口。
 *
 * <p>原 {@code application.app.command.handler.*} 与 {@code application.app.query.handler.*}
 * 已全量内联进本类， 平行 CQRS 树 {@code application/app/**} 随之撤销（Q5：分层约定在模块内唯一）。适配器只依赖本类，HTTP 契约保持不变。
 *
 * <p>本类不出现读侧 DSL：应用分页的关键字 / 状态条件下沉 {@link BoneApplicationRepository#findPage}（本聚合读，ADR-0030），
 * 权限绑定查询走 {@link AppPermissionRepository}（E-4.2）。
 */
@Service
@RequiredArgsConstructor
public class AppApplicationService {

  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;

  private final BoneApplicationRepository boneApplicationRepository;
  private final AppPermissionRepository appPermissionRepository;
  private final AccountRepository accountRepository;

  @Transactional
  public Long createApplication(CreateApplicationCommand cmd) {
    BoneApplication app =
        BoneApplication.create(
            cmd.getName(), cmd.getCode(), cmd.getDescription(), cmd.getIcon(), 0L);
    return boneApplicationRepository.save(app);
  }

  @Transactional
  public void updateApplication(UpdateApplicationCommand cmd) {
    BoneApplication app = boneApplicationRepository.findById(cmd.getId());
    if (app == null) {
      throw new BizException(NOT_FOUND, "应用不存在");
    }
    app.update(cmd.getName(), cmd.getDescription(), cmd.getIcon(), cmd.getStatus());
    boneApplicationRepository.save(app);
  }

  @Transactional
  public void deleteApplication(Long id) {
    boneApplicationRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public PageResult<ApplicationDTO> pageApplications(ApplicationPageQuery qry) {
    PageResult<BoneApplication> result =
        boneApplicationRepository.findPage(
            qry.getKeyword(),
            qry.getStatus(),
            qry.getPage() != null ? qry.getPage() : 1,
            qry.getSize() != null ? qry.getSize() : 10);
    List<ApplicationDTO> list =
        result.getRecords().stream().map(AppApplicationService::toDto).collect(Collectors.toList());
    return PageResult.of(list, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public ApplicationDTO applicationDetail(Long id) {
    BoneApplication entity = boneApplicationRepository.findById(id);
    if (entity == null) {
      throw new BizException(NOT_FOUND, "应用不存在");
    }
    return toDto(entity);
  }

  /** 某应用的权限绑定列表（批量回填用户名，避免逐行查账号的 N+1）。 */
  @Transactional(readOnly = true)
  public List<AppPermissionDTO> permissions(Long appId) {
    List<AppPermission> permissions = appPermissionRepository.findByApp(appId);
    if (permissions.isEmpty()) {
      return List.of();
    }
    List<Long> userIds = permissions.stream().map(AppPermission::getUserId).distinct().toList();
    Map<Long, String> usernameById =
        accountRepository.findByIds(userIds).stream()
            .filter(account -> account.getUsername() != null)
            .collect(
                Collectors.toMap(
                    Account::getId, account -> account.getUsername().value(), (a, b) -> a));
    return permissions.stream()
        .map(permission -> toPermissionDto(permission, usernameById.get(permission.getUserId())))
        .collect(Collectors.toList());
  }

  /** 授予用户在应用内的权限；同一 (appId, userId) 已存在绑定时改为变更角色（幂等授予）。 */
  @Transactional
  public void grantPermission(GrantAppPermissionCommand cmd) {
    if (cmd.getAppId() == null) {
      throw new BizException(BAD_REQUEST, "应用ID不能为空");
    }
    if (cmd.getUserId() == null) {
      throw new BizException(BAD_REQUEST, "用户ID不能为空");
    }
    BoneApplication app = boneApplicationRepository.findById(cmd.getAppId());
    if (app == null) {
      throw new BizException(NOT_FOUND, "应用不存在");
    }
    AppRole role;
    try {
      role = AppRole.fromExternal(cmd.getRole());
    } catch (IllegalArgumentException e) {
      throw new BizException(BAD_REQUEST, e.getMessage());
    }
    AppPermission existing =
        appPermissionRepository.findByAppAndUser(cmd.getAppId(), cmd.getUserId()).orElse(null);
    if (existing != null) {
      existing.changeRole(role);
      appPermissionRepository.update(existing);
      return;
    }
    appPermissionRepository.save(
        AppPermission.create(cmd.getAppId(), cmd.getUserId(), role, app.getTenantId()));
  }

  /** 移除用户在应用内的权限；绑定不存在时静默返回（幂等移除）。 */
  @Transactional
  public void revokePermission(Long appId, Long userId) {
    appPermissionRepository
        .findByAppAndUser(appId, userId)
        .ifPresent(permission -> appPermissionRepository.deleteById(permission.getId()));
  }

  private static ApplicationDTO toDto(BoneApplication entity) {
    ApplicationDTO dto = new ApplicationDTO();
    dto.setId(entity.getId());
    dto.setName(entity.getName());
    dto.setCode(entity.getCode());
    dto.setDescription(entity.getDescription());
    dto.setIcon(entity.getIcon());
    dto.setStatus(entity.getStatus());
    dto.setModuleCount(0);
    dto.setEntityCount(0);
    dto.setMyRole(null);
    dto.setCreatedAt(entity.getCreatedAt());
    dto.setUpdatedAt(entity.getUpdatedAt());
    return dto;
  }

  private static AppPermissionDTO toPermissionDto(AppPermission permission, String username) {
    AppPermissionDTO dto = new AppPermissionDTO();
    dto.setUserId(permission.getUserId());
    dto.setUsername(username);
    dto.setRole(permission.getRole().externalName());
    dto.setCreatedAt(permission.getCreatedAt());
    return dto;
  }
}
