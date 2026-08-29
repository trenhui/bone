package com.bone.metadata.engine.runtime.validation.strategy;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.runtime.RuleEngine;
import com.bone.metadata.engine.runtime.validation.ValidationResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 自定义业务规则验证策略 处理实体的自定义业务规则验证 */
public class CustomRuleValidationStrategy implements ValidationStrategy {

  private static final Logger log = LoggerFactory.getLogger(CustomRuleValidationStrategy.class);
  private final RuleEngine ruleEngine;

  public CustomRuleValidationStrategy(RuleEngine ruleEngine) {
    this.ruleEngine = ruleEngine;
  }

  @Override
  public void validate(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
    log.debug("开始验证自定义业务规则，实体类型: {}", entityMetadata.getApiName());

    try {
      // 如果RuleEngine可用，委托给它处理
      if (ruleEngine != null) {
        log.debug("委托业务规则验证给规则引擎");
        // 使用默认的触发事件
        List<String> triggerEvents = Arrays.asList("CREATE", "UPDATE");
        // 获取验证结果Map
        Map<String, Object> ruleResult =
            ruleEngine.validateRules(entityMetadata.getApiName(), entityData, triggerEvents);

        // 检查验证是否通过
        Boolean isValid = (Boolean) ruleResult.get("valid");
        if (isValid != null && !isValid) {
          // 获取规则验证结果中的错误列表
          @SuppressWarnings("unchecked")
          List<String> ruleErrors = (List<String>) ruleResult.get("errors");
          if (ruleErrors != null && !ruleErrors.isEmpty()) {
            for (String error : ruleErrors) {
              ValidationResult.ValidationError validationError =
                  ValidationResult.ValidationError.builder()
                      .fieldPath("general")
                      .message(error)
                      .build();
              result.addError(validationError);
            }
          }
        }
      } else {
        // 降级到简单验证逻辑
        fallbackCustomRuleValidation(entityMetadata, entityData, result);
      }
    } catch (Exception e) {
      log.error("验证自定义规则过程发生异常", e);
      // 添加异常信息到验证结果
      ValidationResult.ValidationError validationError =
          ValidationResult.ValidationError.builder()
              .fieldPath("general")
              .message("业务规则验证异常: " + e.getMessage())
              .build();
      result.addError(validationError);
    }

    log.debug("自定义业务规则验证完成，实体类型: {}", entityMetadata.getApiName());
  }

  /** 降级验证逻辑（当RuleEngine不可用时） */
  private void fallbackCustomRuleValidation(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
    List<?> rules = entityMetadata.getValidationRules();
    if (rules == null || rules.isEmpty()) {
      return;
    }

    log.debug("使用降级逻辑验证自定义规则，规则数量: {}", rules.size());
    // 简化的降级验证逻辑
  }

  @Override
  public String getName() {
    return "customRuleValidation";
  }
}
