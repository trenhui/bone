package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

@Data
public class MetaEntityDTO {
  private Long id;
  private String name;
  private String code;
  private String displayName;
  private String description;
  private String tableName;
  private Integer type;
  private Integer deliveryMode;
  private String deliveryModeLabel;
  private Integer status;
  private String statusLabel;
  private Integer sortOrder;
  private String icon;

  /** 所属 IAM 模块（应用→模块→模型分层导航用；未归属为 null） */
  private Long moduleId;

  /** 乐观锁版本（PUT 使用 If-Match: "v{version}"） */
  private Integer version;

  /** 自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）； 空 = 未启用。编辑表单据此回填子配置。 */
  private String autoNumber;

  /**
   * 行级访问策略 JSON（引擎形态 {@code {"owner_field","rules":[{"name","effect","when"}]}} 或简写数组 {@code
   * [{"code"}]}）；空 = 不启用行过滤（退回「本人可见」以外的引擎缺省）。
   *
   * <p><b>必须回传</b>：PUT 是「传 null 即清空」的全量语义，读不到就回填 ⇒ 建模者改任一字段再保存会<b>静默清空行策略</b> （表现为「审批人打开单据
   * 404」且无任何提示），属安全策略静默失效，比报错危险得多。
   */
  private String rowPolicies;

  /** 是否配置团队级界面 schema（列表批量填充，防 N+1；null=未知，建模页按 false 回落）。 */
  private Boolean hasUiSchema;
}
