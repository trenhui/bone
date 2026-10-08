package com.bone.metadata.engine.runtime.rule;

import com.bone.metadata.engine.runtime.ExpressionEngine;
import com.bone.metadata.engine.runtime.MetadataEngine;
import com.bone.metadata.engine.runtime.RuleEngine;
import com.bone.metadata.engine.runtime.ValidationEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 规则引擎自动配置类 负责管理规则引擎相关组件的初始化和依赖注入
 *
 * <p><b>为什么必须在这里回接 ruleEngine</b>：{@link ValidationEngine} 带 {@code @Component} 且有 3 个构造器， Spring
 * 只能选中无参构造器（无 {@code @Autowired} 标注）⇒ 其 {@code expressionEngine} 与 {@code ruleEngine} 字段恒为 null ⇒
 * {@code validateCustomRules} 恒走 else 分支的 {@code fallbackCustomRuleValidation}， 而该方法曾是空实现 ⇒
 * <b>元数据自定义业务规则校验在生产上完全静默跳过（fail-open）</b>。 这里显式把它接回去，让规则校验真正生效。
 */
@AutoConfiguration
public class RuleEngineAutoConfiguration {

  /**
   * 把规则引擎注入校验引擎。
   *
   * <p>用 {@link ObjectProvider} 而非直接参数注入：宿主可能没装 starter（本工程 {@code bone-metadata-server} 只依赖 {@code
   * engine-runtime}），此时容器里没有 {@code ValidationEngine} bean，provider 会解析成 {@code null}，
   * 不至于让整个应用启动失败。
   */
  @Bean
  public RuleEngineValidationEngineWiring ruleEngineValidationEngineWiring(
      ObjectProvider<ValidationEngine> validationEngineProvider, RuleEngine ruleEngine) {
    return new RuleEngineValidationEngineWiring(
        validationEngineProvider.getIfAvailable(), ruleEngine);
  }

  /** 创建规则引擎Bean */
  @Bean
  @ConditionalOnMissingBean
  public RuleEngine ruleEngine(ExpressionEngine expressionEngine, MetadataEngine metadataEngine) {
    return new RuleEngine(expressionEngine, metadataEngine);
  }

  /**
   * 校验引擎与规则引擎的接线。
   *
   * <p>在构造时立即执行注入，而不是等到别处显式调用：{@code ValidationEngine} 的 setter 注入是本工程唯一的 修复入口，若只把两个引用存进 record 而不调
   * setter，等于什么都没修（会再次退化成 fail-open 且更难发现）。
   */
  public record RuleEngineValidationEngineWiring(
      ValidationEngine validationEngine, RuleEngine ruleEngine) {
    public RuleEngineValidationEngineWiring {
      if (validationEngine != null) {
        validationEngine.setRuleEngine(ruleEngine);
      }
    }
  }
}
