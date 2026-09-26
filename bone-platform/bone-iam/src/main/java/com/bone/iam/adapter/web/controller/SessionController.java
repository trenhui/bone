package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.application.SessionApplicationService;
import com.bone.iam.domain.model.session.Session;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理：基于 {@code iam_refresh_token} 的"在线会话列表 + 强制下线"。
 *
 * <p>权限码：
 *
 * <ul>
 *   <li>{@code iam:sessions:read} —— 查询会话列表
 *   <li>{@code iam:sessions:write} —— 单条/账号全量吊销
 * </ul>
 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1)
@RequiredArgsConstructor
public class SessionController {

  private final SessionApplicationService sessionApplicationService;

  @GetMapping("/accounts/{accountId}/sessions")
  @PreAuthorize("hasAuthority('iam:sessions:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<List<Session>> list(@PathVariable Long accountId) {
    return ApiResponse.success(sessionApplicationService.list(accountId));
  }

  @DeleteMapping("/sessions/{id}")
  @PreAuthorize("hasAuthority('iam:sessions:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> revokeOne(@PathVariable Long id) {
    sessionApplicationService.revokeOne(id);
    return ApiResponse.success();
  }

  @DeleteMapping("/accounts/{accountId}/sessions")
  @PreAuthorize("hasAuthority('iam:sessions:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Integer> revokeAll(@PathVariable Long accountId) {
    return ApiResponse.success(sessionApplicationService.revokeAllForAccount(accountId));
  }
}
