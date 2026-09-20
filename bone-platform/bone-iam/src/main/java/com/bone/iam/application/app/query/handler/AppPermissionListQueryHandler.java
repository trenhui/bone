package com.bone.iam.application.app.query.handler;

import com.bone.iam.application.app.query.dto.AppPermissionDTO;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.app.AppPermission;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AppPermissionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 查询某个应用的权限绑定列表，并批量回填用户名字。 */
@Component
@RequiredArgsConstructor
public class AppPermissionListQueryHandler {

  private final AppPermissionRepository appPermissionRepository;
  private final AccountRepository accountRepository;

  @Transactional(readOnly = true)
  public List<AppPermissionDTO> handle(Long appId) {
    List<AppPermission> permissions = appPermissionRepository.findByApp(appId);
    if (permissions.isEmpty()) {
      return List.of();
    }
    Map<Long, String> usernameById = loadUsernames(permissions);
    return permissions.stream()
        .map(permission -> toDto(permission, usernameById.get(permission.getUserId())))
        .collect(Collectors.toList());
  }

  private Map<Long, String> loadUsernames(List<AppPermission> permissions) {
    List<Long> userIds = permissions.stream().map(AppPermission::getUserId).distinct().toList();
    return accountRepository.findByIds(userIds).stream()
        .filter(account -> account.getUsername() != null)
        .collect(
            Collectors.toMap(
                Account::getId, account -> account.getUsername().value(), (a, b) -> a));
  }

  private AppPermissionDTO toDto(AppPermission permission, String username) {
    AppPermissionDTO dto = new AppPermissionDTO();
    dto.setUserId(permission.getUserId());
    dto.setUsername(username);
    dto.setRole(permission.getRole().externalName());
    dto.setCreatedAt(permission.getCreatedAt());
    return dto;
  }
}
