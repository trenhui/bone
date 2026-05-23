package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreateAccountReq;
import com.bone.iam.adapter.web.dto.req.UpdateAccountReq;
import com.bone.iam.adapter.web.dto.resp.AccountDetailResp;
import com.bone.iam.application.command.cmd.CreateAccountCommand;
import com.bone.iam.application.command.cmd.UpdateAccountCommand;
import com.bone.iam.application.query.dto.AccountDTO;
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
        cmd.setRoleIds(req.getRoleIds());
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
        resp.setRoleIds(dto.getRoleIds());
        resp.setLastLoginAt(dto.getLastLoginAt());
        resp.setLastLoginIp(dto.getLastLoginIp());
        resp.setCreatedAt(dto.getCreatedAt());
        resp.setUpdatedAt(dto.getUpdatedAt());
        return resp;
    }
}
