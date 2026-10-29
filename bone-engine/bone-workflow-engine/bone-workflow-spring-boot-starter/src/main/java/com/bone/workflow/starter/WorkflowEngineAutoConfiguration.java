package com.bone.workflow.starter;

import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowBranchEvaluator;
import com.bone.workflow.domain.WorkflowConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowSimulator;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.NotificationPort;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInboxQuery;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.ports.spi.WorkflowUnitOfWork;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.PersistenceNotificationPort;
import com.bone.workflow.starter.designer.WorkflowDefinitionCommandService;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import com.bone.workflow.starter.designer.WorkflowSimulationService;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDraftSdkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
   * 试运行器（纯函数）：与引擎共用同一个条件求值器 bean，保证「模拟说走 A」＝「实际走 A」。
   *
   * <p>宿主若替换了 {@link WorkflowConditionEvaluator}（例如改成与平台 UI 协议规则同源的实现），试运行自动跟着换—— 这正是把它做成注入而不是
   * {@code new} 的理由。
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowSimulator workflowSimulator(WorkflowConditionEvaluator conditionEvaluator) {
    return new WorkflowSimulator(
        new WorkflowBranchEvaluator(conditionEvaluator), conditionEvaluator);
  }

  /**
   * 设计态写路径：草稿保存 / 校验 / 发布 / 上下线。
   *
   * <p>装配所需的两张表仓储由 {@link com.bone.workflow.starter.config.WorkflowSdkRepositoryConfig} 注册； {@code
   * ObjectMapper} 用容器里那一份（与 {@code SdkWorkflowDefinitionRepository} 反序列化同一实例，
   * 避免出现「保存时能解析、运行期不能解析」的双 mapper 分叉）。
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowDefinitionCommandService workflowDefinitionCommandService(
      WfProcessDraftSdkRepository drafts,
      WfProcessDefSdkRepository definitions,
      WorkflowDefinitionValidator validator,
      WorkflowIdGenerator idGenerator,
      ObjectMapper objectMapper) {
    return new WorkflowDefinitionCommandService(
        drafts, definitions, validator, idGenerator, objectMapper);
  }

  /** 发布前试运行：路径 + 分支判定明细 + 每节点审批人解析结果。 */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowSimulationService workflowSimulationService(
      WorkflowSimulator simulator,
      WorkflowDefinitionValidator validator,
      ApproverResolver approverResolver,
      ObjectMapper objectMapper) {
    return new WorkflowSimulationService(simulator, validator, approverResolver, objectMapper);
  }

  /**
   * 设计器授权缺省实现：<b>fail-closed（一律拒绝，403）</b>。
   *
   * <p>放在这里而不是 web 自动装配里，是因为「谁能改流程定义」与「有没有 web 层」无关——纯领域库宿主也可能自己调命令服务， 端口本身应当始终可取用。
   *
   * <p>为什么缺省拒绝：本 starter 不掌握宿主的身份上下文（既不知道 JWT 语义，也不知道网关头的可信度），因此不知道该放谁进来。 此时「谁都不放」是唯一安全的默认——第一次调用就以
   * 403 + 接线提示暴露「你还没接授权」，而不是安静地让任意调用方发布 / 下线 线上流程定义。这与平台既有的 ACL fail-closed 口径（{@code
   * DenyDocAcl}）一致。
   *
   * <p>接线方式（任一）：
   *
   * <ul>
   *   <li>提供自己的 {@link WorkflowDesignerAuthorizer} bean —— {@code @ConditionalOnMissingBean}
   *       保证优先生效；
   *   <li>本地联调显式认账：{@code bone.workflow.designer.allow-anonymous=true}（等价于 allowAll，<b>不要带到生产</b>）。
   * </ul>
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowDesignerAuthorizer workflowDesignerAuthorizer(
      @Value("${bone.workflow.designer.allow-anonymous:false}") boolean allowAnonymous) {
    return allowAnonymous
        ? WorkflowDesignerAuthorizer.allowAll()
        : WorkflowDesignerAuthorizer.denyAll();
  }

  /**
   * 缺省通知端口：<b>站内信持久化</b>（写入 {@code wf_notification}，办理人据此查收件箱）。
   *
   * <p>审批流若只改状态不通知，会退化成「办事人每天自己刷待办列表」——催办、超时、改派全部失效，故通知是一等能力， 默认即落库（P0-3：通知
   * MUST）。通知失败绝不阻断审批主链路（端口内部已吞异常）。
   *
   * <p><b>挂起告警接收人</b>：{@code bone.workflow.notify.admin-role} 配置角色码后，告警经 {@link
   * com.bone.workflow.starter.spi.RoleMemberProvider} 解析成真实成员逐人投递；未配置 / SPI 未接入 / 角色解析为空时回落兜底接收人并打
   * WARN （告警绝不静默丢，但接收人质量降级必须可诊断）。
   *
   * <p>宿主若把通知接到 IM / 邮件网关，提供自己的 {@link NotificationPort} bean
   * 即覆盖本实现（{@code @ConditionalOnMissingBean} 保证不冲突）；如需保留站内信收件箱入口，可同时提供 {@code WorkflowInboxQuery}
   * 实现。本 bean 缺失时引擎回退到 {@link NotificationPort#NOOP}，不会因此不可用。
   */
  @Bean
  @ConditionalOnMissingBean
  public NotificationPort workflowNotificationPort(
      WfNotificationSdkRepository notificationRepository,
      WorkflowIdGenerator idGenerator,
      ObjectProvider<com.bone.workflow.starter.spi.RoleMemberProvider> roleMemberProvider,
      ObjectProvider<com.bone.workflow.starter.metrics.WorkflowMetrics> metricsProvider,
      @Value("${bone.workflow.notify.admin-role:}") String adminRole) {
    PersistenceNotificationPort port =
        new PersistenceNotificationPort(
            notificationRepository, idGenerator, null, metricsProvider.getIfAvailable());
    port.setAdminRole(adminRole, roleMemberProvider.getIfAvailable());
    return port;
  }

  /**
   * 缺省收件箱查询端口（读能力）：复用默认通知端口——{@link PersistenceNotificationPort} 写读一体， 既「发得出」也「查得到」。
   *
   * <p><b>为什么单独一个 bean</b>：{@link NotificationPort} 的注入点类型是写端口，宿主入站面按 {@link WorkflowInboxQuery}
   * 类型注入时拿不到它（接口与实现类型不同）。若宿主的 NotificationPort 同时实现本接口（例如自己的持久化通知），直接复用； 若宿主接到
   * IM/邮件网关（写不落本地表），收件箱无数据源——给<b>空实现 + WARN</b>，让「站内信 Tab 是空的」可诊断，而不是启动失败。
   *
   * <p>宿主可自备 {@link WorkflowInboxQuery} bean 覆盖（{@code @ConditionalOnMissingBean} 保证不冲突）。
   *
   * <p><b>为什么必须 {@code @Primary}</b>：默认通知端口 {@link PersistenceNotificationPort} 同时实现了写端口与读端口， 故容器里按
   * {@link WorkflowInboxQuery} 类型看会有<b>两个</b>候选（本 bean + {@code workflowNotificationPort}，指向同一实例）。
   * 缺 {@code @Primary} 时，入站面用 {@code ObjectProvider#getIfAvailable()} 取端口会抛 {@code
   * NoUniqueBeanDefinitionException}——实测把单据详情页打成 500（挂起原因读不到还连累整个详情）。 读端口语义上就该是这一个（本 bean
   * 是唯一承诺「按读类型可注入」的定义）。
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public WorkflowInboxQuery workflowInboxQuery(
      ObjectProvider<NotificationPort> notificationPortProvider) {
    NotificationPort notificationPort = notificationPortProvider.getIfAvailable();
    if (notificationPort instanceof WorkflowInboxQuery inbox) {
      return inbox;
    }
    logInboxFallback(notificationPort);
    return (tenantId, recipientId, unreadOnly) -> List.of();
  }

  /** 收件箱空实现的降级告警：宿主自备 NotificationPort 但没提供读端口，站内信 Tab 将永远为空。 */
  private static void logInboxFallback(NotificationPort notificationPort) {
    LoggerFactory.getLogger(WorkflowEngineAutoConfiguration.class)
        .warn(
            "[workflow] 宿主 NotificationPort（{}）未实现 WorkflowInboxQuery，站内信收件箱降级为空实现——"
                + "审批通知仍会写入宿主通道，但「我的通知」页无数据。如需站内信入口，请提供 WorkflowInboxQuery bean。",
            notificationPort == null ? "缺失" : notificationPort.getClass().getSimpleName());
  }

  /**
   * 引擎本体：无条件装配（7 端口由集成配置保证齐备；缺失则 fail-fast，并点名缺失端口）。
   *
   * <p>见类注释「为什么不再用 {@code @ConditionalOnBean} 判定端口」——不能用类型条件门禁，否则回到静默不装配。
   *
   * <p><b>可观测性</b>：7 个端口经 {@link ObjectProvider} 取用，任一缺失即抛出点名缺失类型与排查路径的 {@link
   * IllegalStateException}（比 Spring 泛化的 {@code NoSuchBeanDefinitionException} 更可定位）；
   * 装配成功后打印就绪日志，声明绑定了哪个具体端口实现——杜绝「引擎是否装配 / 绑了哪个状态端口」再次沦为只能靠 {@code /actuator} 排查的静默事实。
   *
   * <p><b>显式关闭开关</b>：{@code bone.workflow.engine.enabled=false} 可整体关闭引擎（默认 true）。 硬装配意味着「只要 starter
   * 在场就必然装配」，宿主若确实只要领域规则不要引擎，用该开关显式关—— 关闭是<b>显式选择</b>而非「端口碰巧缺失就静默跳过」，后者正是此前事故的成因。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "bone.workflow.engine", name = "enabled", matchIfMissing = true)
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
        ObjectProvider<NotificationPort> notificationPortProvider,
        ObjectProvider<org.springframework.transaction.PlatformTransactionManager>
            txManagerProvider) {
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

      // 用例级事务边界：一次审批决策是「多次写」的用例，单条 SQL 的原子性覆盖不到中间崩溃窗口
      // （节点 CAS 已推进、派单还没落库 ⇒ 零待办零告警）。缺事务管理器时只能退化为直通，但必须响亮告警——
      // 静默退化等于把 P0 的崩溃窗口留着而没人知道。
      org.springframework.transaction.PlatformTransactionManager txManager =
          txManagerProvider.getIfAvailable();
      WorkflowUnitOfWork unitOfWork;
      if (txManager == null) {
        unitOfWork = WorkflowUnitOfWork.DIRECT;
        log.warn(
            "[workflow] 未找到 PlatformTransactionManager：引擎写路径「无事务保护」，"
                + "「审批决策」等多次写用例在进程中断时会留下半完成状态。生产环境请确保数据源已装配事务管理器。");
      } else {
        unitOfWork = new com.bone.workflow.starter.tx.TransactionalWorkflowUnitOfWork(txManager);
      }

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
              notificationPort,
              unitOfWork);

      log.info(
          "WorkflowEngine 已装配：definitionRepository={}, instanceRepository={}, recordPort={}, statusPort={}, auditPort={}, idGenerator={}, notificationPort={}",
          simpleName(def),
          simpleName(inst),
          simpleName(rec),
          simpleName(status),
          simpleName(audit),
          simpleName(gen),
          simpleName(notificationPort));
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
     * 代理友好类型名：bean 常被 Spring 代理，直接用 {@code getClass().getSimpleName()} 会打出 {@code
     * Xxx$$SpringCGLIB$$0}（CGLIB）或 {@code $Proxy123}（JDK 动态代理）这类代理名，误导排障。 用 {@code
     * AopProxyUtils.ultimateTargetClass} 取最终目标类，两类代理都能还原为真实实现类名 （此前手截 {@code $$} 只覆盖 CGLIB，JDK
     * 代理仍会漏成 {@code $Proxy123}）。
     */
    private static String simpleName(Object bean) {
      return AopProxyUtils.ultimateTargetClass(bean).getSimpleName();
    }
  }
}
