package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreateRoleReq;
import com.bone.iam.application.command.cmd.CreateRoleCmd;
import org.springframework.stereotype.Component;

@Component
public class RoleWebConverter {
    public CreateRoleCmd toCreateRoleCmd(CreateRoleReq req) {
        CreateRoleCmd cmd = new CreateRoleCmd();
        cmd.setName(req.getName());
        cmd.setCode(req.getCode());
        cmd.setDescription(req.getDescription());
        cmd.setTenantId(req.getTenantId());
        return cmd;
    }
}