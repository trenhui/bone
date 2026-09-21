package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateMenuReq;
import com.bone.iam.adapter.web.dto.request.UpdateMenuReq;
import com.bone.iam.application.command.CreateMenuCommand;
import com.bone.iam.application.command.UpdateMenuCommand;
import org.springframework.stereotype.Component;

@Component
public class MenuWebConverter {

  public CreateMenuCommand toCreateMenuCommand(CreateMenuReq req) {
    CreateMenuCommand cmd = new CreateMenuCommand();
    cmd.setName(req.getName());
    cmd.setParentId(req.getParentId());
    cmd.setPath(req.getPath());
    cmd.setIcon(req.getIcon());
    cmd.setOrderNo(req.getOrderNo());
    cmd.setPermission(req.getPermission());
    cmd.setType(req.getType());
    cmd.setTenantId(req.getTenantId());
    return cmd;
  }

  public UpdateMenuCommand toUpdateMenuCommand(Long id, UpdateMenuReq req) {
    UpdateMenuCommand cmd = new UpdateMenuCommand();
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setParentId(req.getParentId());
    cmd.setPath(req.getPath());
    cmd.setIcon(req.getIcon());
    cmd.setOrderNo(req.getOrderNo());
    cmd.setPermission(req.getPermission());
    cmd.setType(req.getType());
    return cmd;
  }
}
