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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
   * 引擎本体：无条件装配（7 端口由集成配置保证齐备；缺失则 fail-fast，并点名缺失端口）。
   *
   * <p>见类注释「为什么不再用 {@code @ConditionalOnBean} 判定端口」——不能用类型条件门禁，否则回到静默不装配。
   *
   * <p><b>可观测性</b>：7 个端口经 {@link ObjectProvider} 取用，任一缺失即抛出点名缺失类型与排查路径的 {@link
   * IllegalStateException}（比 Spring 泛化的 {@code NoSuchBeanDefinitionException} 更可定位）；
   * 装配成功后打印就绪日志，声明绑定了哪个具体端口实现——杜绝「引擎是否装配 / 绑了哪个状态端口」再次沦为只能靠 {@code /actuator} 排查的静默事实。
   */
  @Configuration(proxyBeanMethods = false)
  static class EngineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EngineConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    WorkflowEngine workflowEngine(
        ObjectProvider<WorkflowDefinitionRepository> definitionRepository,
        ObjectProvider<WorkflowInstanceRepository> instanceRepository,
        ObjectProvider<ApproverResolver> approverResolver,
        ObjectProvider<WorkflowRecordPort> recordPort,
        ObjectProvider<WorkflowStatusPort> statusPort,
        ObjectProvider<WorkflowAuditPort> auditPort,
        ObjectProvider<WorkflowIdGenerator> idGenerator,
        ObjectProvider<WorkflowConditionEvaluator> conditionEvaluatorProvider,
        ObjectProvider<WorkflowDefinitionValidator> validatorProvider,
        ObjectProvider<java.time.Clock> clockProvider,
        ObjectProvider<NotificationPort> notificationPortProvider) {
      WorkflowDefinitionRepository def =
          require(definitionRepository, WorkflowDefinitionRepository.class);
      WorkflowInstanceRepository inst =
          require(instanceRepository, WorkflowInstanceRepository.class);
      ApproverResolver resolver = require(approverResolver, ApproverResolver.class);
      WorkflowRecordPort rec = require(recordPort, WorkflowRecordPort.class);
      WorkflowStatusPort status = require(statusPort, WorkflowStatusPort.class);
      WorkflowAuditPort audit = require(auditPort, WorkflowAuditPort.class);
      WorkflowIdGenerator gen = require(idGenerator, WorkflowIdGenerator.class);

      WorkflowConditionEvaluator conditionEvaluator =
          conditionEvaluatorProvider.getIfAvailable(StructuredConditionEvaluator::new);
      WorkflowDefinitionValidator validator =
          validatorProvider.getIfAvailable(WorkflowDefinitionValidator::new);
      java.time.Clock clock = clockProvider.getIfAvailable(java.time.Clock::systemUTC);
      NotificationPort notificationPort =
          notificationPortProvider.getIfAvailable(() -> NotificationPort.NOOP);

      WorkflowEngine engine =
          new WorkflowEngineImpl(
              def,
              inst,
              resolver,
              rec,
              status,
              audit,
              gen,
              conditionEvaluator,
              validator,
              clock,
              notificationPort);

      log.info(
          "WorkflowEngine 已装配：definitionRepository={}, instanceRepository={}, recordPort={}, statusPort={}, auditPort={}, idGenerator={}",
          simpleName(def),
          simpleName(inst),
          simpleName(rec),
          simpleName(status),
          simpleName(audit),
          simpleName(gen));
      return engine;
    }

    /** 取端口：缺失即抛出点名缺失类型与排查路径的异常，绝不静默跳过。 */
    private static <T> T require(ObjectProvider<T> provider, Class<T> type) {
      T bean = provider.getIfAvailable();
      if (bean == null) {
        throw new IllegalStateException(
            "工作流引擎装配失败：缺少端口实现 "
                + type.getName()
                + "。请确认 WorkflowBoneIntegrationAutoConfiguration 已在 classpath（其 @ConditionalOnClass(SqlExecutor.class) 需 bone-metadata-sdk 在场），"
                + "且宿主已提供 JdbcRuntimeRecordService / RuntimeActionApplicationService 等基建（否则集成配置按 @ConditionalOnBean 跳过对应端口）。");
      }
      return bean;
    }

    /**
     * 代理友好类型名：SDK 仓储等 bean 常被 Spring CGLIB 代理，直接用 {@code getClass().getSimpleName()} 会打出 {@code
     * Xxx$$SpringCGLIB$$0} 这类代理名，误导排障，故截去 {@code $$} 之后的代理后缀。
     */
    private static String simpleName(Object bean) {
      String name = bean.getClass().getSimpleName();
      int i = name.indexOf("$$");
      return i > 0 ? name.substring(0, i) : name;
    }
  }
}
