package com.bone.iam.application.app.command.handler;

import com.bone.core.exception.BizException;
import com.bone.iam.application.app.command.GrantAppPermissionCommand;
import com.bone.iam.domain.app.AppPermission;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.iam.domain.app.vo.AppRole;
import com.bone.iam.domain.repository.AppPermissionRepository;
import com.bone.iam.domain.repository.BoneApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 授予用户在应用内的权限。同一 (appId, userId) 已存在绑定时改为变更角色（幂等授予）。
 *
 * <p>注册前校验应用真实存在，避免产生悬空的应用权限。
 */
@Component
@RequiredArgsConstructor
public class GrantAppPermissionCommandHandler {

  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;

  private final AppPermissionRepository appPermissionRepository;
  private final BoneApplicationRepository boneApplicationRepository;

  @Transactional
  public void handle(GrantAppPermissionCommand cmd) {
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
}
