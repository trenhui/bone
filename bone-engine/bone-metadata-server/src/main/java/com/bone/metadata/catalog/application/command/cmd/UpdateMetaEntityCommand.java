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

  /**
   * 行级 ACL 策略 JSON（形态同 {@code CreateMetaEntityCommand#rowPolicies}）。
   *
   * <p>全量替换语义：不传即清空（清空后引擎退回 {@code created_by} 单列 owner 规则）。 仅草稿实体可改——已发布实体的行策略直接决定线上可见性，
   * 放开会让「改字段」顺带改掉数据可见边界。
   */
  private String rowPolicies;
}
