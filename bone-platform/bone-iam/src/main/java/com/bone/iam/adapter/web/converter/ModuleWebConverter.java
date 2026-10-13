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
    // 同 AppWebConverter：路径变量 id 是定位键，漏设 ⇒ updateModule 以 null 查库 500 且改动静默丢弃。
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setDescription(req.getDescription());
    cmd.setStatus(req.getStatus());
    return cmd;
  }
}
