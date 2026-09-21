package com.bone.iam.application.command;

import java.util.Map;
import lombok.Data;

@Data
public class UpdateAuditSettingsCommand {

  private Map<String, Object> settings;
}
