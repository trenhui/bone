package com.bone.workflow.starter.designer;

import com.bone.workflow.domain.WorkflowDefinitionValidator.Violation;
import java.util.List;

/**
 * 流程定义不满足发布条件（fail-closed）。
 *
 * <p>宿主映射建议：<b>422</b>，并把 {@link #violations()} 原样放进响应体——违规清单是设计器的核心产物（哪个节点缺什么、
 * 点一下能跳过去），把它压成一句话等于让用户回去猜。
 *
 * <p>命名刻意不带 {@code BusinessException} 后缀（平台架构守护禁止该后缀，统一走 {@code
 * com.bone.core.exception.BizException}）， 而引擎侧刻意不依赖平台核心包，宿主自行翻译。
 */
public class WorkflowDefinitionInvalidException extends RuntimeException {

  private final String definitionKey;
  private final List<Violation> violations;

  public WorkflowDefinitionInvalidException(String definitionKey, List<Violation> violations) {
    super(
        "流程定义 "
            + definitionKey
            + " 校验不通过（"
            + violations.size()
            + " 项），拒绝发布："
            + violations.stream()
                .limit(3)
                .map(violation -> violation.code() + " " + violation.message())
                .reduce((left, right) -> left + "；" + right)
                .orElse(""));
    this.definitionKey = definitionKey;
    this.violations = violations == null ? List.of() : List.copyOf(violations);
  }

  public String definitionKey() {
    return definitionKey;
  }

  public List<Violation> violations() {
    return violations;
  }
}
