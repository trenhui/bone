package com.bone.workflow.starter;

import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.NotificationPort;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.LoggingNotificationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审批流引擎自动装配。
 *
 * <p><b>装配边界</b>：引擎本体只有在宿主提供了<b>全部端口</b>时才装配——端口（定义仓储 / 实例仓储 / 指派解析 / 业务数据 / 状态推进 / 审计 / id 生成）由平台侧
 * 集成配置（{@link WorkflowBoneIntegrationAutoConfiguration}）提供。缺端口不是引擎的错（可能是只想引入纯领域规则的
 * 场景），因此此时<b>不</b>装配引擎，而不是抛异常把宿主启动打挂。
 *
 * <p><b>为什么必须显式 {@code @AutoConfigureAfter}</b>：{@code EngineConfiguration} 的
 * {@code @ConditionalOnBean} 按<b>类型</b> 判定 7 个端口 bean 是否在场——该条件只有在端口 bean
 * <b>定义已注册</b>后才可能成立，因此本配置必须排在集成配置<b>之后</b>。 当前类名字母序（{@code WorkflowBone... <
 * WorkflowEngine...}）碰巧满足，但那是巧合：任一类改名即复现 2026-10-21 实证过的 「引擎静默不装配、零报错」事故。顺序必须显式声明，不依赖命名。
 */
@AutoConfiguration
@AutoConfigureAfter(WorkflowBoneIntegrationAutoConfiguration.class)
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

  /**
   * 缺省通知端口：日志兜底 + 挂起告警。
   *
   * <p>宿主接入站内信 / IM / 邮件时，提供自己的 {@link NotificationPort} bean 即覆盖本实现。 <b>通知是尽力而为</b>：本 bean 缺失时引擎用
   * {@link NotificationPort#NOOP}，不会因此不可用。
   */
  @Bean
  @ConditionalOnMissingBean
  public NotificationPort workflowNotificationPort() {
    return new LoggingNotificationPort();
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
        ObjectProvider<java.time.Clock> clockProvider,
        ObjectProvider<NotificationPort> notificationPortProvider) {
      WorkflowConditionEvaluator conditionEvaluator =
          conditionEvaluatorProvider.getIfAvailable(StructuredConditionEvaluator::new);
      WorkflowDefinitionValidator validator =
          validatorProvider.getIfAvailable(WorkflowDefinitionValidator::new);
      java.time.Clock clock = clockProvider.getIfAvailable(java.time.Clock::systemUTC);
      NotificationPort notificationPort =
          notificationPortProvider.getIfAvailable(() -> NotificationPort.NOOP);
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
          clock,
          notificationPort);
    }
  }
}
