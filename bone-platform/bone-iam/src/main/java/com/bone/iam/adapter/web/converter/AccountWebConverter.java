package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreateAccountReq;
import com.bone.iam.adapter.web.dto.req.UpdateAccountReq;
import com.bone.iam.application.command.cmd.CreateAccountCmd;
import com.bone.iam.application.command.cmd.UpdateAccountCmd;
import org.springframework.stereotype.Component;

@Component
public class AccountWebConverter {

    public CreateAccountCmd toCreateAccountCmd(CreateAccountReq req) {
        CreateAccountCmd cmd = new CreateAccountCmd();
        cmd.setUsername(req.getUsername());
        cmd.setPassword(req.getPassword());
        cmd.setEmail(req.getEmail());
        cmd.setPhone(req.getPhone());
        cmd.setRealName(req.getRealName());
        cmd.setTenantId(req.getTenantId());
        cmd.setRoleIds(req.getRoleIds());
        return cmd;
    }

    public UpdateAccountCmd toUpdateAccountCmd(Long id, UpdateAccountReq req) {
        UpdateAccountCmd cmd = new UpdateAccountCmd();
        cmd.setId(id);
        cmd.setEmail(req.getEmail());
        cmd.setPhone(req.getPhone());
        cmd.setRealName(req.getRealName());
        cmd.setStatus(req.getStatus());
        cmd.setRoleIds(req.getRoleIds());
        return cmd;
    }
}
