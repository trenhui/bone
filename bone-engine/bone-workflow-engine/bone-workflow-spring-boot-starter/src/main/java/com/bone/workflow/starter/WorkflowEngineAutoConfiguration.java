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
import com.bone.workflow.starter.adapter.PersistenceNotificationPort;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审批流引擎自动装配。
 *
 * <p><b>装配边界（硬装配，fail-fast）</b>：引擎本体一律装配——7 个端口（定义仓储 / 实例仓储 / 指派解析 / 业务数据 / 状态推进 / 审计 / id
 * 生成）由平台侧集成配置（{@link WorkflowBoneIntegrationAutoConfiguration}）在同一 starter
 * 内无条件提供，合法部署里必然齐备。若真有端口缺失（例如 {@code SqlExecutor} 类不在 classpath 导致集成配置整体跳过），由 Spring 在 {@code
 * workflowEngine} 注入点抛出点名缺失端口的清晰异常，<b>而不是静默不装配引擎</b>。
 *
 * <p><b>为什么不再用 {@code @ConditionalOnBean} 判定端口</b>：此前用 {@code @ConditionalOnBean(7 端口)} 门禁引擎，
 * 但该条件在内嵌配置类上按类型判定 7 个端口时不可靠——集成配置的 {@code @Bean} 定义尚未对条件评估可见时即被判定为「缺失」， 复现 2026-10-21 / 2026-10-25
 * 两次实证过的「引擎静默不装配、零报错」事故（{@code /actuator/conditions} 可见：仅组件扫描的 {@code ExpenseWorkflowStatusPort}
 * 可见，其余 6 个集成端口均报 did-not-find）。 故改为硬装配：端口齐备（常态）即装配；真缺则 fail-fast，问题当场暴露。
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
   * 缺省通知端口：<b>站内信持久化</b>（写入 {@code wf_notification}，办理人据此查收件箱）。
   *
   * <p>审批流若只改状态不通知，会退化成「办事人每天自己刷待办列表」——催办、超时、改派全部失效，故通知是一等能力， 默认即落库（P0-3：通知
   * MUST）。通知失败绝不阻断审批主链路（端口内部已吞异常）。
   *
   * <p>宿主若把通知接到 IM / 邮件网关，提供自己的 {@link NotificationPort} bean
   * 即覆盖本实现（{@code @ConditionalOnMissingBean} 保证不冲突）；如需保留站内信收件箱入口，可同时提供 {@code WorkflowInboxQuery}
   * 实现。本 bean 缺失时引擎回退到 {@link NotificationPort#NOOP}，不会因此不可用。
   */
  @Bean
  @ConditionalOnMissingBean
  public NotificationPort workflowNotificationPort(
      WfNotificationSdkRepository notificationRepository, WorkflowIdGenerator idGenerator) {
    return new PersistenceNotificationPort(notificationRepository, idGenerator);
  }

  /**
   * 引擎本体：无条件装配（7 端口由集成配置保证齐备；缺失则注入点 fail-fast）。
   *
   * <p>见类注释「为什么不再用 {@code @ConditionalOnBean} 判定端口」——不能用类型条件门禁，否则回到静默不装配。
   */
  @Configuration(proxyBeanMethods = false)
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
