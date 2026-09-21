package com.bone.system.application.command;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class UpdateAlertRuleCommand {
  @NotNull(message = "规则ID不能为空")
  private Long id;

  private String name;

  private String description;

  private Double threshold;

  private String alertLevel;

  private List<String> notificationChannels;
}
