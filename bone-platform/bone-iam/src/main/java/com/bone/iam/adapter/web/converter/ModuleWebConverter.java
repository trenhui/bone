package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateModuleReq;
import com.bone.iam.adapter.web.dto.request.UpdateModuleReq;
import com.bone.iam.application.command.CreateModuleCommand;
import com.bone.iam.application.command.UpdateModuleCommand;
import org.springframework.stereotype.Component;

@Component
public class ModuleWebConverter {
  public CreateModuleCommand toCreateCommand(CreateModuleReq req, Long appId) {
    CreateModuleCommand cmd = new CreateModuleCommand();
    cmd.setAppId(appId);
    cmd.setName(req.getName());
    cmd.setCode(req.getCode());
    cmd.setDescription(req.getDescription());
    return cmd;
  }

  public UpdateModuleCommand toUpdateCommand(UpdateModuleReq req, Long id) {
    UpdateModuleCommand cmd = new UpdateModuleCommand();
    cmd.setName(req.getName());
    cmd.setDescription(req.getDescription());
    cmd.setStatus(req.getStatus());
    return cmd;
  }
}
