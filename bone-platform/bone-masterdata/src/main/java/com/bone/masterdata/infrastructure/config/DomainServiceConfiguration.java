package com.bone.masterdata.infrastructure.config;

import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.masterdata.domain.service.quality.RuleExpressionEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 领域服务显式装配：领域服务不带 Spring 注解，统一在此注册。 */
@Configuration
public class DomainServiceConfiguration {

  @Bean
  public DataQualityService dataQualityService() {
    return new DataQualityService();
  }

  @Bean
  public RuleExpressionEvaluator ruleExpressionEvaluator() {
    return new RuleExpressionEvaluator();
  }
}
