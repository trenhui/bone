package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.CreateRoleReq;
import com.bone.iam.adapter.web.dto.resp.RoleDetailResp;
import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.application.query.dto.RoleDetailDTO;
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

    public RoleDetailResp toDetailResp(RoleDetailDTO dto) {
        RoleDetailResp resp = new RoleDetailResp();
        resp.setId(dto.getId());
        resp.setName(dto.getName());
        resp.setDescription(dto.getDescription());
        resp.setTenantId(dto.getTenantId());
        resp.setCreatedAt(dto.getCreatedAt());
        resp.setUpdatedAt(dto.getUpdatedAt());
        resp.setPermissionIds(dto.getPermissionIds());
        return resp;
    }
}