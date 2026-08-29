package com.bone.metadata.engine.domain.metadata;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作步骤定义 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationStep {
  private String name;

  private String description;

  private StepType type;

  private String targetEntity; // 目标实体
  private String action; // 操作动作

  @Builder.Default private Map<String, Object> parameters = new HashMap<>();

  private String condition; // 执行条件

  @Builder.Default private Integer order = 0;

  @Builder.Default private boolean required = true;

  public enum StepType {
    DATA_QUERY, // 数据查询
    DATA_UPDATE, // 数据更新
    DATA_CREATE, // 数据创建
    VALIDATION, // 数据验证
    EXTERNAL_CALL, // 外部调用
    NOTIFICATION, // 发送通知
    APPROVAL, // 审批流程
    CALCULATION, // 计算处理
    HOOK_EXECUTION // 钩子执行
  }
}
