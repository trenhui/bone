package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreatePermissionReq;
import com.bone.iam.application.command.cmd.CreatePermissionCmd;
import org.springframework.stereotype.Component;

@Component
public class PermissionWebConverter {
    public CreatePermissionCmd toCreatePermissionCmd(CreatePermissionReq req) {
        CreatePermissionCmd cmd = new CreatePermissionCmd();
        cmd.setCode(req.getCode());
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        cmd.setParentId(req.getParentId());
        cmd.setType(req.getType());
        return cmd;
    }
}