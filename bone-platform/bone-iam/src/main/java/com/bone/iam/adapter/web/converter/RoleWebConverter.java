package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.AssignPermissionReq;
import com.bone.iam.adapter.web.dto.request.CreateRoleReq;
import com.bone.iam.adapter.web.dto.request.UpdateRoleReq;
import com.bone.iam.adapter.web.dto.response.RoleDetailResp;
import com.bone.iam.application.command.AssignPermissionCommand;
import com.bone.iam.application.command.CreateRoleCommand;
import com.bone.iam.application.command.UpdateRoleCommand;
import com.bone.iam.application.query.dto.RoleDetailDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class RoleWebConverter {
  public CreateRoleCommand toCreateRoleCommand(CreateRoleReq req) {
    CreateRoleCommand cmd = new CreateRoleCommand();
    cmd.setName(req.getName());
    cmd.setCode(req.getCode());
    cmd.setDescription(req.getDescription());
    cmd.setTenantId(req.getTenantId());
    return cmd;
  }

  public UpdateRoleCommand toUpdateRoleCommand(Long id, UpdateRoleReq req) {
    UpdateRoleCommand cmd = new UpdateRoleCommand();
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setDescription(req.getDescription());
    return cmd;
  }

  public AssignPermissionCommand toAssignPermissionCommand(Long roleId, AssignPermissionReq req) {
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(roleId);
    cmd.setPermissionIds(req.getPermissionIds());
    return cmd;
  }

  public RoleDetailResp toDetailResp(RoleDetailDTO dto) {
    RoleDetailResp resp = new RoleDetailResp();
    resp.setId(dto.getId());
    resp.setName(dto.getName());
    resp.setDescription(dto.getDescription());
    resp.setTenantId(dto.getTenantId());
    resp.setCreatedAt(toInstant(dto.getCreatedAt()));
    resp.setUpdatedAt(toInstant(dto.getUpdatedAt()));
    resp.setPermissionIds(dto.getPermissionIds());
    return resp;
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
