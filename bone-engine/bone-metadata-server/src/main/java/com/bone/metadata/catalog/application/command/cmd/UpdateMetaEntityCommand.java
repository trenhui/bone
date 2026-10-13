package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMetaEntityCommand {
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer sortOrder;

  /** 0-GENERATIVE 1-RUNTIME；仅草稿可改 */
  private Integer deliveryMode;

  private String icon;

  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）。
   * 仅草稿实体可改；发布后结构锁死，更新请求中的该值将被忽略（domain update 在 PUBLISHED 时整体拒绝）。
   */
  private String autoNumber;
}
