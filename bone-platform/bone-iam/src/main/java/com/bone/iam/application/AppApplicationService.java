package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.CreateApplicationCommand;
import com.bone.iam.application.command.GrantAppPermissionCommand;
import com.bone.iam.application.command.UpdateApplicationCommand;
import com.bone.iam.application.query.dto.AppPermissionDTO;
import com.bone.iam.application.query.dto.ApplicationDTO;
import com.bone.iam.application.query.qry.ApplicationPageQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.app.AppPermission;
import com.bone.iam.domain.model.app.BoneApplication;
import com.bone.iam.domain.model.app.valueobject.AppRole;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AppPermissionRepository;
import com.bone.iam.domain.repository.BoneApplicationRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用（应用 / 应用内权限绑定）应用层统一门面（Application Service First）——应用类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code application.app.command.handler.*} 与 {@code application.app.query.handler.*}
 * 已全量内联进本类， 平行 CQRS 树 {@code application/app/**} 随之撤销（Q5：分层约定在模块内唯一）。适配器只依赖本类，HTTP 契约保持不变。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL：应用分页的关键字 / 状态条件下沉 {@link BoneApplicationRepository#findPage}（本聚合读，ADR-0030），
 * 权限绑定查询走 {@link AppPermissionRepository}（E-4.2）。
 */
/*
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（BoneApplication / AppPermission）当前不发布领域事件，其创建/更新/授权绑定均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class AppApplicationService {

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
      throw IamErrors.of(IamErrorCodes.APPLICATION_NOT_FOUND, "应用不存在");
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

  /**
   * 「我的应用」：返回当前用户（按 {@code userId}）在 {@code bone_app_permission} 中有绑定的应用。
   *
   * <p><b>可见性策略（G15）</b>：① {@code userId == null}（无主体上下文，如安全关闭的 E2E）回退为「全部应用」，保持旧行为； ② 平台管理员（{@code
   * is_admin}）可见全部应用（其角色体现为治理视角，不依赖逐应用授权）；③ 其余用户仅见自己被授权的应用， 且回填 {@code myRole} 供前端做只读/可建模判断。
   */
  @Transactional(readOnly = true)
  public PageResult<ApplicationDTO> myApps(Long userId, ApplicationPageQuery qry) {
    if (userId == null) {
      return pageApplications(qry);
    }
    Account account = accountRepository.findById(userId);
    if (account != null && Boolean.TRUE.equals(account.isAdmin())) {
      return pageApplications(qry);
    }
    List<AppPermission> permissions = appPermissionRepository.findByUser(userId);
    if (permissions.isEmpty()) {
      return PageResult.of(List.of(), 0L, safePage(qry), safeSize(qry));
    }
    Set<Long> appIds =
        permissions.stream().map(AppPermission::getAppId).collect(Collectors.toSet());
    Map<Long, String> roleByApp =
        permissions.stream()
            .collect(
                Collectors.toMap(
                    AppPermission::getAppId, p -> p.getRole().externalName(), (a, b) -> a));
    List<ApplicationDTO> matched =
        appIds.stream()
            .map(boneApplicationRepository::findById)
            .filter(Objects::nonNull)
            .filter(app -> matchesKeyword(app, qry.getKeyword()))
            .filter(app -> qry.getStatus() == null || qry.getStatus().equals(app.getStatus()))
            .map(
                app -> {
                  ApplicationDTO dto = toDto(app);
                  dto.setMyRole(roleByApp.get(app.getId()));
                  return dto;
                })
            .collect(Collectors.toList());
    int page = safePage(qry);
    int size = safeSize(qry);
    int from = Math.min((page - 1) * size, matched.size());
    int to = Math.min(from + size, matched.size());
    return PageResult.of(matched.subList(from, to), (long) matched.size(), page, size);
  }

  private static boolean matchesKeyword(BoneApplication app, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return true;
    }
    String k = keyword.toLowerCase();
    return (app.getName() != null && app.getName().toLowerCase().contains(k))
        || (app.getCode() != null && app.getCode().toLowerCase().contains(k));
  }

  private static int safePage(ApplicationPageQuery qry) {
    return qry.getPage() != null && qry.getPage() > 0 ? qry.getPage() : 1;
  }

  private static int safeSize(ApplicationPageQuery qry) {
    return qry.getSize() != null && qry.getSize() > 0 ? qry.getSize() : 10;
  }

  @Transactional(readOnly = true)
  public ApplicationDTO applicationDetail(Long id) {
    BoneApplication entity = boneApplicationRepository.findById(id);
    if (entity == null) {
      throw IamErrors.of(IamErrorCodes.APPLICATION_NOT_FOUND, "应用不存在");
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
      throw IamErrors.of(IamErrorCodes.APPLICATION_ID_REQUIRED, "应用ID不能为空");
    }
    if (cmd.getUserId() == null) {
      throw IamErrors.of(IamErrorCodes.USER_ID_REQUIRED, "用户ID不能为空");
    }
    BoneApplication app = boneApplicationRepository.findById(cmd.getAppId());
    if (app == null) {
      throw IamErrors.of(IamErrorCodes.APPLICATION_NOT_FOUND, "应用不存在");
    }
    AppRole role;
    try {
      role = AppRole.fromExternal(cmd.getRole());
    } catch (DomainException e) {
      throw IamErrors.of(IamErrorCodes.APP_ROLE_INVALID, e.getMessage());
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
