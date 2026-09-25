package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.LoginReq;
import com.bone.iam.adapter.web.dto.response.LoginResp;
import com.bone.iam.application.command.LoginCommand;
import com.bone.iam.domain.model.account.Account;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthWebConverter {
  public LoginCommand toLoginCommand(LoginReq req, String clientIp) {
    LoginCommand cmd = new LoginCommand();
    cmd.setUsername(req.getUsername());
    cmd.setPassword(req.getPassword());
    cmd.setClientIp(clientIp);
    return cmd;
  }

  public LoginResp toLoginResp(Map<String, Object> result) {
    LoginResp resp = new LoginResp();
    resp.setToken((String) result.get("token"));
    resp.setRefreshToken((String) result.get("refreshToken"));
    Object requireChange = result.get("requirePasswordChange");
    resp.setRequirePasswordChange(requireChange instanceof Boolean b ? b : Boolean.FALSE);

    Account account = (Account) result.get("account");
    LoginResp.AccountInfo accountInfo = new LoginResp.AccountInfo();
    accountInfo.setId(account.getId());
    accountInfo.setUsername(account.getUsername().value());
    accountInfo.setEmail(account.getEmail().value());
    accountInfo.setPhone(account.getPhone());
    accountInfo.setRealName(account.getRealName());
    accountInfo.setAvatarUrl(account.getAvatarUrl());
    accountInfo.setStatus(account.getStatus().getCode());
    accountInfo.setIsAdmin(account.isAdmin());
    accountInfo.setLastLoginAt(account.getLastLoginAt());
    accountInfo.setCreatedAt(account.getCreatedAt());
    // 身份分流字段（详设 §2.9）：由 AuthApplicationService 随登录结果一并返回
    Object tenantId = result.get("tenantId");
    accountInfo.setTenantId(tenantId instanceof Long l ? l : account.getTenantId());
    Object tenantName = result.get("tenantName");
    accountInfo.setTenantName(tenantName instanceof String s ? s : null);
    resp.setAccount(accountInfo);

    return resp;
  }
}
