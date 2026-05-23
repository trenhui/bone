package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMetaEntityCommand {
  @NotBlank private String name;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer sortOrder;
  /** 0-GENERATIVE 1-RUNTIME；仅草稿可改 */
  private Integer deliveryMode;
  private String icon;
}
