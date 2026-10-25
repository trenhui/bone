package com.bone.workflow.starter;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.runtime.application.RuntimeActionApplicationService;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.starter.adapter.ActionExecutionAuditPort;
import com.bone.workflow.starter.adapter.PlatformWorkflowIdGenerator;
import com.bone.workflow.starter.adapter.RecordFieldApproverResolver;
import com.bone.workflow.starter.adapter.RuntimeRecordDataPort;
import com.bone.workflow.starter.adapter.SdkWorkflowDefinitionRepository;
import com.bone.workflow.starter.adapter.SdkWorkflowInstanceRepository;
import com.bone.workflow.starter.binding.WorkflowBindingRepository;
import com.bone.workflow.starter.outbox.OutboxStatusPort;
import com.bone.workflow.starter.outbox.RuntimeActionIntentExecutor;
import com.bone.workflow.starter.outbox.StatusIntentExecutor;
import com.bone.workflow.starter.outbox.StatusProjectionRelay;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import com.bone.workflow.starter.sdk.WfActiveInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfBindingSdkRepository;
import com.bone.workflow.starter.sdk.WfInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfStatusOutboxSdkRepository;
import com.bone.workflow.starter.sdk.WfTaskSdkRepository;
import com.bone.workflow.starter.spi.RoleMemberProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 审批流引擎的<b>平台桥接自动装配</b>。
 *
 * <p>本配置只负责把引擎的 7 个端口接到 BONE 平台上；引擎本体（{@link WorkflowEngine}）由同模块的 {@link
 * WorkflowEngineAutoConfiguration} 装配（显式 {@code @AutoConfigureAfter} 保证端口 bean
 * 先注册）。两侧解耦，平台耦合全部收敛在本配置类。
 *
 * <p><b>端口硬装配</b>：starter 反向依赖 {@code bone-metadata-server}，{@code JdbcRuntimeRecordService} 与
 * {@code RuntimeActionApplicationService} 在合法部署里必然存在，端口 bean 直接注入这两个依赖——不再用
 * {@code @ConditionalOnBean} 去判定组件扫描型 bean（自动配置阶段对其判定不可靠，会导致引擎静默不装配）。依赖缺失时由 Spring 在注入点
 * fail-fast（报错信息清晰），而非静默跳过。
 *
 * <p><b>持久化通道</b>：{@code wf_*} 全部经 bone-metadata-sdk 的 {@code @Sql} 代理（{@link
 * com.bone.workflow.starter.config.WorkflowSdkRepositoryConfig} 注册）——2026-10-24 收敛，HC-006 豁免已移除。
 *
 * <p><b>启用前提（L3，需架构师审批）</b>：{@code wf_instance} / {@code wf_task} / {@code wf_process_def} / {@code
 * wf_active_instance} / {@code wf_status_outbox} / {@code wf_binding} 六张表必须已建（DDL 见设计文档 {@code
 * doc/design/modules/12. 工作流引擎设计.md} §7，迁移 {@code
 * scripts/migration/0056_wf_workflow_tables.sql}）。表不存在时，本模块的读写会在运行期报错——这是刻意不兜底的： 审批流数据落错表比启动失败更危险。
 *
 * <p><b>类级别条件的前置变迁</b>：收敛到 SDK 前，本配置以 {@code @ConditionalOnClass(NamedParameterJdbcTemplate)} 为门禁
 * （适配器直接持 JdbcTemplate）。持久化改走 SDK {@code @Sql} 后，真实前置是 SDK 的 SQL 执行入口 {@code SqlExecutor}——
 * 门禁随之切换，装配层对 spring-jdbc 的最后一个引用也一并消失（ArchUnit「starter 零 JDBC」规则因此无需再给自动配置类开豁免）。
 */
@AutoConfiguration
@ConditionalOnClass(SqlExecutor.class)
public class WorkflowBoneIntegrationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowInstanceRepository workflowInstanceRepository(
      WfInstanceSdkRepository instances,
      WfTaskSdkRepository tasks,
      WfActiveInstanceSdkRepository activePointers) {
    return new SdkWorkflowInstanceRepository(instances, tasks, activePointers);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowDefinitionRepository workflowDefinitionRepository(
      WfProcessDefSdkRepository definitions, ObjectMapper objectMapper) {
    return new SdkWorkflowDefinitionRepository(definitions, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowAuditPort workflowAuditPort() {
    return new ActionExecutionAuditPort();
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowIdGenerator workflowIdGenerator(SqlExecutor sqlExecutor) {
    return new PlatformWorkflowIdGenerator(sqlExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowRecordPort workflowRecordPort(JdbcRuntimeRecordService recordService) {
    return new RuntimeRecordDataPort(recordService);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowStatusOutboxRepository workflowStatusOutboxRepository(
      WfStatusOutboxSdkRepository outbox) {
    return new WorkflowStatusOutboxRepository(outbox);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowStatusPort workflowStatusPort(
      RuntimeActionApplicationService actionService,
      WorkflowIdGenerator idGenerator,
      WorkflowStatusOutboxRepository outbox) {
    // 默认走发件箱（最终一致 + 可重试 + 可对账）。
    // 宿主若需强同步，可自备 WorkflowStatusPort bean 覆盖本实现（@ConditionalOnMissingBean 保证不冲突）：
    // 在自备实现里同步调用 RuntimeActionApplicationService.invokeAction 即可。代价是平台动作失败会让这次投影
    // 静默丢失（「引擎说批了、单据状态没动」且无迹可查）——这正是默认走发件箱要避免的。
    return new OutboxStatusPort(outbox, idGenerator::nextId);
  }

  @Bean
  @ConditionalOnMissingBean
  public StatusIntentExecutor workflowStatusIntentExecutor(
      RuntimeActionApplicationService actionService,
      WorkflowInstanceRepository instanceRepository) {
    return new RuntimeActionIntentExecutor(actionService, instanceRepository);
  }

  /**
   * 实体 → 流程绑定访问组件（{@code wf_binding}）。
   *
   * <p>无条件装配（只读一张配置表）：提交动作 handler 经它把 {@code entity_code} 解析成 {@code def_key}， 换流程改配置不发版。表中没有绑定 ⇒
   * handler 侧 fail-fast，不会静默落到某个默认流程上。
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowBindingRepository workflowBindingRepository(WfBindingSdkRepository bindings) {
    return new WorkflowBindingRepository(bindings);
  }

  /**
   * 状态投影中继：把发件箱里的状态意图投递到平台动作服务，最终一致地推进单据状态。
   *
   * <p>注意：{@code @Scheduled} 需宿主开启 {@code @EnableScheduling}；未开启时静默忽略， 宿主也可自行按周期调用 {@code
   * runOnce(...)}。
   */
  @Bean
  @ConditionalOnMissingBean
  public StatusProjectionRelay workflowStatusProjectionRelay(
      WorkflowStatusOutboxRepository outbox, StatusIntentExecutor executor) {
    return new StatusProjectionRelay(outbox, executor, 100, 8);
  }

  /**
   * 指派解析：默认实现处理 USER / FIELD / INITIATOR_SELECT，组织目录相关策略经 {@link RoleMemberProvider} 外接。
   *
   * <p>见 {@link RecordFieldApproverResolver} 的能力边界——平台无「直属主管 / 部门负责人」字段， {@code LINE_MANAGER}
   * 等策略解析不出人并由引擎<b>挂起</b>（失败关闭，不是放行）。
   *
   * <p>宿主可自备 {@link ApproverResolver} bean 覆盖本默认实现（{@code @ConditionalOnMissingBean} 保证不冲突）。
   */
  @Bean
  @ConditionalOnMissingBean
  public ApproverResolver workflowApproverResolver(
      ObjectProvider<RoleMemberProvider> roleMemberProvider) {
    return new RecordFieldApproverResolver(roleMemberProvider.getIfAvailable());
  }
}
