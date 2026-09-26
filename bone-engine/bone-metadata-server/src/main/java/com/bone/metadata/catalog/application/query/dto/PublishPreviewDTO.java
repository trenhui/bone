package com.bone.metadata.catalog.application.query.dto;

import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 发布摘要预览（UC-W7 摘要级 dry-run，先行于 ADR-0039 R1 的完整发布包）。
 *
 * <p>内容 = 实体与字段清单 + 关系数 + 静态校验问题（{@link EntityValidationIssue}） + RUNTIME 实体的物理结构 inspect 结果（{@link
 * PhysicalStructurePlan}，纯只读不执行 DDL，{@code statements} 即将要执行的建表/加列语句）。 {@code
 * hasBlockingErrors=true} 时前端发布按钮禁用；后端发布期仍会兜底校验（漂移 409）。
 */
@Data
@Builder
public class PublishPreviewDTO {

  private Long entityId;

  private String entityCode;

  private Integer status;

  private Integer deliveryMode;

  private List<MetaFieldDTO> fields;

  private long relationCount;

  private List<EntityValidationIssue> validationIssues;

  private boolean hasBlockingErrors;

  /** 无阻断错误即可提交发布（发布期还有漂移/审批兜底，不等于承诺成功） */
  private boolean runnable;

  /** 仅 RUNTIME 实体返回；GENERATIVE 为 null（无物理对齐动作） */
  private PhysicalStructurePlan physical;
}
