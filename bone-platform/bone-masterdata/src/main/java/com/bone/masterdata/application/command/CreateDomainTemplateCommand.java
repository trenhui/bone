package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDomainTemplateCommand {

  /** mdm_domain_template.domain_code 为 NOT NULL 且租户内唯一。 */
  @NotBlank(message = "domainCode: 域编码不能为空")
  private String domainCode;

  @NotBlank(message = "domainName: 域名称不能为空")
  private String domainName;

  private String description;

  /** 默认治理等级：L1/L2/L3，缺省 L1。 */
  private String defaultGovernanceTier;

  /** 默认字段集（JSON 数组字符串）。 */
  private String fieldSchema;

  /** 默认质量规则包（JSON 数组字符串）。 */
  private String ruleSchema;

  /** 默认分类骨架（JSON 树字符串）。 */
  private String categorySchema;
}
