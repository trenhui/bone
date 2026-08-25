package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMetaFieldCommand {
  // entityId 由 URL 路径 {entityId} 注入（见 MetaFieldCatalogController），不由 body 提供，故不加 @NotNull
  private Long entityId;
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  @NotBlank private String type;

  private Integer length;
  private Boolean required;
  private Boolean unique;
  private String defaultValue;
  private String comment;
  private Integer sortOrder;

  /** fieldType 别名，兼容前端传参 */
  private String fieldType;
}
