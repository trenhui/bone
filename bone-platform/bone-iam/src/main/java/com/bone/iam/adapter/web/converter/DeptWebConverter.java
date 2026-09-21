package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateDeptReq;
import com.bone.iam.adapter.web.dto.request.UpdateDeptReq;
import com.bone.iam.application.command.CreateDeptCommand;
import com.bone.iam.application.command.UpdateDeptCommand;
import org.springframework.stereotype.Component;

@Component
public class DeptWebConverter {

  public CreateDeptCommand toCreateDeptCommand(CreateDeptReq req) {
    CreateDeptCommand cmd = new CreateDeptCommand();
    cmd.setName(req.getName());
    cmd.setParentId(req.getParentId());
    cmd.setOrderNo(req.getOrderNo());
    cmd.setStatus(req.getStatus());
    cmd.setTenantId(req.getTenantId());
    return cmd;
  }

  public UpdateDeptCommand toUpdateDeptCommand(Long id, UpdateDeptReq req) {
    UpdateDeptCommand cmd = new UpdateDeptCommand();
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setParentId(req.getParentId());
    cmd.setOrderNo(req.getOrderNo());
    cmd.setStatus(req.getStatus());
    return cmd;
  }
}
