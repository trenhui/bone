package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreatePermissionReq;
import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import org.springframework.stereotype.Component;

@Component
public class PermissionWebConverter {
    public CreatePermissionCommand toCreatePermissionCommand(CreatePermissionReq req) {
        CreatePermissionCommand cmd = new CreatePermissionCommand();
        cmd.setCode(req.getCode());
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        cmd.setResourceType(req.getResourceType());
        cmd.setResourcePath(req.getResourcePath());
        cmd.setAction(req.getAction());
        cmd.setParentId(req.getParentId());
        cmd.setType(req.getType());
        cmd.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return cmd;
    }
}