package com.bone.workflow.starter.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.workflow.starter.sdk.WfActiveInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfBindingSdkRepository;
import com.bone.workflow.starter.sdk.WfInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDraftSdkRepository;
import com.bone.workflow.starter.sdk.WfStatusOutboxSdkRepository;
import com.bone.workflow.starter.sdk.WfTaskSdkRepository;
import org.springframework.context.annotation.Configuration;

/**
 * 注册七张 {@code wf_*} 表的 SDK {@code @Sql} 仓储代理。
 *
 * <p>（2026-10-27 由六张增至七张：新增 {@link WfProcessDraftSdkRepository}——设计器的草稿态。草稿必须独立成表， 理由见该接口类注释：{@code
 * findByVersionRow} 不判 {@code status}，草稿与发布版共用版本号会让读取侧读出两行。）
 *
 * <p><b>为什么独立成一个 {@code @Configuration}</b>：仓储注册只依赖 SDK 基建（SqlExecutor 等五个 bean），
 * 与「把引擎端口接到平台」的桥接装配（{@link WorkflowBoneIntegrationAutoConfiguration}）关注点不同， 且必须排在 SDK
 * 扩展组件扫描之后（{@code AutoConfiguration.imports} 顺序保证）——{@code RepositoryFactoryBean}
 * 解析仓储接口时需要扩展Coordinator 已就位。
 *
 * <p>装配前提由 {@code SqlRepositoryAutoConfiguration} + {@code MetadataAutoConfiguration} 提供
 * （SqlExecutor 只需 jdbc + properties；DelegatingMetadataService → EmbeddedMetadataService 构造期不触库）。
 */
@Configuration
@EnableSqlRepositories(
    basePackageClasses = {
      WfActiveInstanceSdkRepository.class,
      WfBindingSdkRepository.class,
      WfInstanceSdkRepository.class,
      WfNotificationSdkRepository.class,
      WfProcessDefSdkRepository.class,
      WfProcessDraftSdkRepository.class,
      WfStatusOutboxSdkRepository.class,
      WfTaskSdkRepository.class
    })
public class WorkflowSdkRepositoryConfig {}
