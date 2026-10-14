package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMetaEntityCommand {
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer type;

  /** 0-GENERATIVE（默认） 1-RUNTIME */
  private Integer deliveryMode;

  private String icon;

  /** 所属模块（null 表示未分组） */
  private Long moduleId;

  /**
   * 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）。
   * 非空时平台在业务数据写入时按「前缀-日期-序号」生成单据号；为空 = 不启用。 仅对 RUNTIME（自动生成界面）实体生效，GENERATIVE 实体不落物理表故不注入。
   */
  private String autoNumber;

  /**
   * 行级 ACL 策略 JSON（引擎形态，如 {@code
   * {"owner_field":"created_by","rules":[{"name":"owner_or_approver","effect":"allow","when":"created_by
   * = :operator OR approver_id = :operator"}]}}）。
   *
   * <p><b>为什么开这个口</b>：此前只有迁移脚本能写（0038 直接 UPDATE），建模者无法自助 ⇒ UI Schema 的 {@code dataScope.policyCode}
   * 引用恒报 {@code UI-REF-03}，行级 ACL 事实上不可配置。
   *
   * <p>留空 = 引擎退回 {@code created_by} 单列 owner 规则（最小可见性）。 声明形态与语义见 Runtime 数据面设计 §4.7.1；引用校验见 {@code
   * UiSchemaApplicationService#parsePolicyCodes}。
   */
  private String rowPolicies;
}
