package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreateUserReq;
import com.bone.iam.adapter.web.dto.req.UpdateUserReq;
import com.bone.iam.application.command.cmd.CreateUserCmd;
import com.bone.iam.application.command.cmd.UpdateUserCmd;
import org.springframework.stereotype.Component;

@Component
public class UserWebConverter {
    public CreateUserCmd toCreateUserCmd(CreateUserReq req) {
        CreateUserCmd cmd = new CreateUserCmd();
        cmd.setUsername(req.getUsername());
        cmd.setPassword(req.getPassword());
        cmd.setEmail(req.getEmail());
        cmd.setTenantId(req.getTenantId());
        cmd.setRoleIds(req.getRoleIds());
        return cmd;
    }

    public UpdateUserCmd toUpdateUserCmd(String id, UpdateUserReq req) {
        UpdateUserCmd cmd = new UpdateUserCmd();
        cmd.setId(id);
        cmd.setEmail(req.getEmail());
        cmd.setRoleIds(req.getRoleIds());
        return cmd;
    }
}