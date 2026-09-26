package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateAccountReq;
import com.bone.iam.adapter.web.dto.request.ResetPasswordReq;
import com.bone.iam.adapter.web.dto.request.UpdateAccountReq;
import com.bone.iam.adapter.web.dto.response.AccountDetailResp;
import com.bone.iam.application.command.CreateAccountCommand;
import com.bone.iam.application.command.ResetPasswordCommand;
import com.bone.iam.application.command.UpdateAccountCommand;
import com.bone.iam.application.query.dto.AccountDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class AccountWebConverter {

  public CreateAccountCommand toCreateAccountCommand(CreateAccountReq req) {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername(req.getUsername());
    cmd.setPassword(req.getPassword());
    cmd.setEmail(req.getEmail());
    cmd.setPhone(req.getPhone());
    cmd.setRealName(req.getRealName());
    cmd.setTenantId(req.getTenantId());
    cmd.setDeptId(req.getDeptId());
    cmd.setRoleIds(req.getRoleIds());
    return cmd;
  }

  public UpdateAccountCommand toUpdateAccountCommand(Long id, UpdateAccountReq req) {
    UpdateAccountCommand cmd = new UpdateAccountCommand();
    cmd.setId(id);
    cmd.setEmail(req.getEmail());
    cmd.setPhone(req.getPhone());
    cmd.setRealName(req.getRealName());
    cmd.setStatus(req.getStatus());
    cmd.setDeptId(req.getDeptId());
    cmd.setRoleIds(req.getRoleIds());
    return cmd;
  }

  public ResetPasswordCommand toResetPasswordCommand(Long id, ResetPasswordReq req) {
    ResetPasswordCommand cmd = new ResetPasswordCommand();
    cmd.setId(id);
    cmd.setNewPassword(req.getNewPassword());
    return cmd;
  }

  public AccountDetailResp toDetailResp(AccountDTO dto) {
    AccountDetailResp resp = new AccountDetailResp();
    resp.setId(dto.getId());
    resp.setUsername(dto.getUsername());
    resp.setEmail(dto.getEmail());
    resp.setPhone(dto.getPhone());
    resp.setRealName(dto.getRealName());
    resp.setAvatarUrl(dto.getAvatarUrl());
    resp.setStatus(dto.getStatus());
    resp.setIsAdmin(dto.getIsAdmin());
    resp.setDeptId(dto.getDeptId());
    resp.setDeptName(dto.getDeptName());
    resp.setRoleIds(dto.getRoleIds());
    resp.setLastLoginAt(toInstant(dto.getLastLoginAt()));
    resp.setLastLoginIp(dto.getLastLoginIp());
    resp.setCreatedAt(toInstant(dto.getCreatedAt()));
    resp.setUpdatedAt(toInstant(dto.getUpdatedAt()));
    return resp;
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
