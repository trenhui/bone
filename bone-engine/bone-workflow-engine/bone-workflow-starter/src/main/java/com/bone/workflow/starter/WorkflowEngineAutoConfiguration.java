package com.bone.workflow.starter;

import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审批流引擎自动装配。
 *
 * <p><b>装配边界</b>：引擎本体只有在宿主提供了<b>全部端口</b>时才装配——端口（定义仓储 / 实例仓储 / 指派解析 / 业务数据 / 状态推进 / 审计 / id 生成）由平台侧
 * bridge 提供。缺端口不是引擎的错（可能是只想引入纯领域规则的 场景），因此此时<b>不</b>装配引擎，而不是抛异常把宿主启动打挂。
 */
@AutoConfiguration
public class WorkflowEngineAutoConfiguration {

  /**
   * 缺省条件求值器（结构化 JSON 条件，纯函数）。
   *
   * <p>宿主若要求与平台 UI 协议规则<b>逐条同源</b>（前端先拦、后端强制同一结论），可注册自己的 {@link WorkflowConditionEvaluator} 实现覆盖本
   * bean。
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowConditionEvaluator workflowConditionEvaluator() {
    return new StructuredConditionEvaluator();
  }

  /** 缺省发布期校验器。 */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowDefinitionValidator workflowDefinitionValidator() {
    return new WorkflowDefinitionValidator();
  }

  /** 引擎本体：仅在全部端口就位时装配。 */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean({
    WorkflowDefinitionRepository.class,
    WorkflowInstanceRepository.class,
    ApproverResolver.class,
    WorkflowRecordPort.class,
    WorkflowStatusPort.class,
    WorkflowAuditPort.class,
    WorkflowIdGenerator.class
  })
  static class EngineConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorkflowEngine workflowEngine(
        WorkflowDefinitionRepository definitionRepository,
        WorkflowInstanceRepository instanceRepository,
        ApproverResolver approverResolver,
        WorkflowRecordPort recordPort,
        WorkflowStatusPort statusPort,
        WorkflowAuditPort auditPort,
        WorkflowIdGenerator idGenerator,
        ObjectProvider<WorkflowConditionEvaluator> conditionEvaluatorProvider,
        ObjectProvider<WorkflowDefinitionValidator> validatorProvider,
        ObjectProvider<java.time.Clock> clockProvider) {
      WorkflowConditionEvaluator conditionEvaluator =
          conditionEvaluatorProvider.getIfAvailable(StructuredConditionEvaluator::new);
      WorkflowDefinitionValidator validator =
          validatorProvider.getIfAvailable(WorkflowDefinitionValidator::new);
      java.time.Clock clock = clockProvider.getIfAvailable(java.time.Clock::systemUTC);
      return new WorkflowEngineImpl(
          definitionRepository,
          instanceRepository,
          approverResolver,
          recordPort,
          statusPort,
          auditPort,
          idGenerator,
          conditionEvaluator,
          validator,
          clock);
    }
  }
}
