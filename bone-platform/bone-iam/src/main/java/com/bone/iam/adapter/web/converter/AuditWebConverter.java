package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.resp.AuditSettingsResp;
import com.bone.iam.application.query.dto.AuditSettingsDTO;
import org.springframework.stereotype.Component;

@Component
public class AuditWebConverter {

  public AuditSettingsResp toResp(AuditSettingsDTO dto) {
    AuditSettingsResp resp = new AuditSettingsResp();
    resp.setRetentionDays(dto.getRetentionDays());
    resp.setAutoArchiveEnabled(dto.getAutoArchiveEnabled());
    resp.setArchiveAfterDays(dto.getArchiveAfterDays());
    resp.setStorageType(dto.getStorageType());
    resp.setWormEnabled(dto.getWormEnabled());
    return resp;
  }
}
