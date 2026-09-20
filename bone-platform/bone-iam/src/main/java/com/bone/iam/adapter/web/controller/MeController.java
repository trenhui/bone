package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.dto.request.ChangeMyPasswordReq;
import com.bone.iam.adapter.web.dto.request.UpdateMyProfileReq;
import com.bone.iam.adapter.web.dto.response.MeResp;
import com.bone.iam.application.AccountApplicationService;
import com.bone.iam.application.command.cmd.ChangeMyPasswordCommand;
import com.bone.iam.application.command.cmd.UpdateMyProfileCommand;
import com.bone.iam.application.port.out.CurrentPrincipalPort;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录账号的自助端点：仅依赖"已认证"，不需要额外权限码。
 *
 * <ul>
 *   <li>{@code GET /api/v1/iam/me} —— 我的资料 + scopes
 *   <li>{@code PUT /api/v1/iam/me} —— 更新昵称/手机/头像
 *   <li>{@code POST /api/v1/iam/me/change-password} —— 旧密码校验后改密
 * </ul>
 *
 * <p>当前主体经 {@link CurrentPrincipalPort}（application 出站端口）获取，不再直接调用 {@code infrastructure.security}
 * 的静态工具（E-10.1）。
 */
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/me")
@RequiredArgsConstructor
public class MeController {

  private final AccountApplicationService accountApplicationService;
  private final CurrentPrincipalPort currentPrincipalPort;

  @GetMapping
  public ApiResponse<MeResp> me() {
    JwtPrincipal principal = requirePrincipal();
    Long accountId = parseAccountId(principal);
    return ApiResponse.success(
        accountApplicationService
            .detail(accountId)
            .map(
                account -> {
                  MeResp resp = new MeResp();
                  resp.setId(account.getId());
                  resp.setUsername(account.getUsername());
                  resp.setEmail(account.getEmail());
                  resp.setPhone(account.getPhone());
                  resp.setRealName(account.getRealName());
                  resp.setAvatarUrl(account.getAvatarUrl());
                  resp.setStatus(account.getStatus());
                  resp.setIsAdmin(account.getIsAdmin());
                  resp.setTenantId(account.getTenantId());
                  resp.setScopes(principal.scopes());
                  resp.setLastLoginAt(account.getLastLoginAt());
                  resp.setPasswordUpdatedAt(account.getPasswordUpdatedAt());
                  return resp;
                })
            .orElseThrow(() -> IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND)));
  }

  @PutMapping
  public ApiResponse<Void> updateProfile(@RequestBody UpdateMyProfileReq req) {
    Long accountId = parseAccountId(requirePrincipal());
    UpdateMyProfileCommand cmd = new UpdateMyProfileCommand();
    cmd.setAccountId(accountId);
    cmd.setRealName(req.getRealName());
    cmd.setPhone(req.getPhone());
    cmd.setAvatarUrl(req.getAvatarUrl());
    accountApplicationService.updateMyProfile(cmd);
    return ApiResponse.success();
  }

  @PostMapping("/change-password")
  public ApiResponse<Void> changePassword(@RequestBody ChangeMyPasswordReq req) {
    Long accountId = parseAccountId(requirePrincipal());
    ChangeMyPasswordCommand cmd = new ChangeMyPasswordCommand();
    cmd.setAccountId(accountId);
    cmd.setOldPassword(req.getOldPassword());
    cmd.setNewPassword(req.getNewPassword());
    accountApplicationService.changePassword(cmd);
    return ApiResponse.success();
  }

  private JwtPrincipal requirePrincipal() {
    return currentPrincipalPort
        .currentPrincipal()
        .orElseThrow(() -> IamErrors.of(IamErrorCodes.PROFILE_OWNERSHIP_DENIED, "未登录"));
  }

  private static Long parseAccountId(JwtPrincipal principal) {
    try {
      return Long.parseLong(principal.userId());
    } catch (NumberFormatException e) {
      throw IamErrors.of(IamErrorCodes.PROFILE_OWNERSHIP_DENIED, "无效的会话");
    }
  }
}
