package com.bone.metadata.engine.rule;

import com.bone.metadata.engine.ExpressionEngine;
import com.bone.metadata.engine.MetadataEngine;
import com.bone.metadata.engine.RuleEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 规则引擎自动配置类 负责管理规则引擎相关组件的初始化和依赖注入 */
@Configuration
public class RuleEngineAutoConfiguration {

  /** 创建表达式缓存Bean */
  @Bean
  @ConditionalOnMissingBean
  public ExpressionCache expressionCache() {
    return new ExpressionCache();
  }

  /** 创建自定义函数注册表Bean */
  @Bean
  @ConditionalOnMissingBean
  public CustomFunctionRegistry customFunctionRegistry() {
    return new CustomFunctionRegistry();
  }

  /** 创建评估上下文工厂Bean */
  @Bean
  @ConditionalOnMissingBean
  public EvaluationContextFactory evaluationContextFactory() {
    return new EvaluationContextFactory();
  }

  /** 创建规则引擎Bean 使用@Primary确保这是首选的RuleEngine实现 */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public RuleEngine ruleEngine(
      ExpressionEngine expressionEngine,
      MetadataEngine metadataEngine,
      ExpressionCache expressionCache,
      EvaluationContextFactory evaluationContextFactory,
      CustomFunctionRegistry customFunctionRegistry) {
    return new RuleEngine(
        expressionEngine,
        metadataEngine,
        expressionCache,
        evaluationContextFactory,
        customFunctionRegistry);
  }
}
