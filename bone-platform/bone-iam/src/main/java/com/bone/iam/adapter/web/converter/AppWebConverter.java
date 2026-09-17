package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateAppReq;
import com.bone.iam.adapter.web.dto.request.UpdateAppReq;
import com.bone.iam.application.app.command.CreateApplicationCommand;
import com.bone.iam.application.app.command.UpdateApplicationCommand;
import org.springframework.stereotype.Component;

@Component
public class AppWebConverter {
  public CreateApplicationCommand toCreateCommand(CreateAppReq req) {
    CreateApplicationCommand cmd = new CreateApplicationCommand();
    cmd.setName(req.getName());
    cmd.setCode(req.getCode());
    cmd.setDescription(req.getDescription());
    cmd.setIcon(req.getIcon());
    return cmd;
  }

  public UpdateApplicationCommand toUpdateCommand(UpdateAppReq req, Long id) {
    UpdateApplicationCommand cmd = new UpdateApplicationCommand();
    cmd.setName(req.getName());
    cmd.setDescription(req.getDescription());
    cmd.setIcon(req.getIcon());
    cmd.setStatus(req.getStatus());
    return cmd;
  }
}
