package com.bone.iam.application.app.command.handler;

import com.bone.iam.domain.repository.AppPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 移除用户在应用内的权限。绑定不存在时静默返回（幂等移除）。 */
@Component
@RequiredArgsConstructor
public class RevokeAppPermissionCommandHandler {

  private final AppPermissionRepository appPermissionRepository;

  @Transactional
  public void handle(Long appId, Long userId) {
    appPermissionRepository
        .findByAppAndUser(appId, userId)
        .ifPresent(permission -> appPermissionRepository.deleteById(permission.getId()));
  }
}
