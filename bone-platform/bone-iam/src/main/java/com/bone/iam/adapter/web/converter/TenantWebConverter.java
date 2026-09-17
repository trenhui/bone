package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.request.CreateTenantReq;
import com.bone.iam.adapter.web.dto.request.UpdateTenantQuotaReq;
import com.bone.iam.adapter.web.dto.request.UpdateTenantReq;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantQuotaCommand;
import org.springframework.stereotype.Component;

@Component
public class TenantWebConverter {

  public CreateTenantCommand toCreateTenantCommand(CreateTenantReq req) {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName(req.getName());
    cmd.setCode(req.getCode());
    cmd.setLevel(req.getLevel());
    cmd.setAdminEmail(req.getAdminEmail());
    return cmd;
  }

  public UpdateTenantCommand toUpdateTenantCommand(Long id, UpdateTenantReq req) {
    UpdateTenantCommand cmd = new UpdateTenantCommand();
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setLevel(req.getLevel());
    cmd.setAdminEmail(req.getAdminEmail());
    return cmd;
  }

  public UpdateTenantQuotaCommand toUpdateTenantQuotaCommand(Long id, UpdateTenantQuotaReq req) {
    UpdateTenantQuotaCommand cmd = new UpdateTenantQuotaCommand();
    cmd.setId(id);
    cmd.setMaxAccounts(req.getMaxAccounts());
    cmd.setMaxRoles(req.getMaxRoles());
    return cmd;
  }
}
