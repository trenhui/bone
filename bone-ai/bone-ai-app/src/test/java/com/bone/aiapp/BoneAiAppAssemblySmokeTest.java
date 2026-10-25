package com.bone.aiapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.aiapp.eval.application.EvalManagementApplicationService;
import com.bone.aiapp.eval.domain.repository.EvalCaseRepository;
import com.bone.aiapp.eval.domain.repository.EvalRunRepository;
import com.bone.aidoc.application.DocumentParseApplicationService;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aiintegration.application.IntegrationApplicationService;
import com.bone.aiintegration.domain.gateway.IntegrationAuditPort;
import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ainotification.application.AlertNotificationApplicationService;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.LlmUsageListener;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.extension.repository.ColumnAllocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * <b>生产拓扑装配级冒烟测试</b>：把 {@link BoneAiAppApplication}（九个 AI 节点库 + 内嵌 eval 的<b>全量</b> Spring
 * 上下文真实装配一次）。
 *
 * <p>与 {@code bone-ai-server}（参考宿主，仅六个库）的冒烟测试互补：本测试覆盖<b>生产拓扑独有的模块</b>—— {@code rag}（含 {@code
 * ChunkRepository} 的 {@code @ConditionalOnBean(name=...)} 修复）、内嵌 {@code eval}（{@code
 * EvalCaseRepository} / {@code EvalRunRepository} 经 {@code @EnableSqlRepositories} 注册）、{@code
 * notification} / {@code integration} 端口编排。 参考宿主绿、生产拓扑红的缝隙正是这类「只在全量聚合时才暴露」的装配故障（例如 {@code
 * tenantProvider} 在三个模块同名、{@code ChunkRepository} 的 FactoryBean 类型条件）——本测试把它们一次性钉死。
 *
 * <p>为何用 H2 而非本地 MySQL：同 {@code BlueprintAssemblySmokeTest} / {@code
 * BoneAiAssemblySmokeTest}——验「能否装配起来」与方言无关， 进程内数据源离线 / CI 无网均可跑；{@code spring.sql.init.mode=never}
 * 保证装配阶段不触真实库。
 */
@SpringBootTest(
    classes = BoneAiAppApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:bone_ai_app_assembly;DB_CLOSE_DELAY=-1;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      // 进程内 H2 的 sa 账号默认口令为空，故此处刻意不显式声明口令：写成空值会被密钥扫描
      // 判为「硬编码口令」而阻断提交（空口令不含任何凭据信息，属扫描器误报的真源）。
      "spring.sql.init.mode=never"
    })
class BoneAiAppAssemblySmokeTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("全量生产拓扑可装配：各节点库应用服务 + SDK 仓储（含 rag / eval）均唯一且可解析")
  void contextAssemblesAllProductionModules() {
    // 网关：默认 MockLlmClient，不触真实 provider
    assertUnique(LlmGatewayApplicationService.class, "LLM 网关门面必须装配");

    // 规则治理：依赖 SDK 生成的 RuleAsset / RuleAssetVersion 仓储
    assertUnique(RuleApplicationService.class, "规则治理服务必须装配（RuleAsset 仓储已注册）");
    assertUnique(RuleQueryApplicationService.class, "规则查询服务必须装配");
    assertUnique(RuleAssetRepository.class, "SDK 必须为 RuleAssetRepository 生成实现");
    assertUnique(RuleAssetVersionRepository.class, "SDK 必须为 RuleAssetVersionRepository 生成实现");

    // 编排 + HITL：条件链 RuleAsset → RulesAutoConfiguration → OrchestrationAutoConfiguration
    assertUnique(OrchestrationApplicationService.class, "编排执行器必须装配（跨自动配置顺序 + 类型条件均已修正）");
    assertUnique(HitlApplicationService.class, "HITL 人工终审服务必须装配（hitl 仓储已注册）");
    assertUnique(HitlItemRepository.class, "SDK 必须为 HitlItemRepository 生成实现");
    assertUnique(HitlDispositionRepository.class, "SDK 必须为 HitlDispositionRepository 生成实现");

    // 文档解析：PDFBox 兜底默认 + Docling sidecar（默认关闭）
    assertUnique(DocumentParser.class, "文档解析器必须唯一（默认 PdfBox）");
    assertUnique(DocumentParseApplicationService.class, "文档解析应用服务必须装配");

    // 检索（生产拓扑独有）：ChunkRepository 的 @ConditionalOnBean(name=...) 修复在此验收
    assertUnique(RagApplicationService.class, "RAG 检索服务必须装配（ChunkRepository 已注册）");
    assertUnique(ChunkRepository.class, "SDK 必须为 ChunkRepository 生成实现（FactoryBean 类型条件已改按名）");

    // 证据链
    assertUnique(EvidenceQueryApplicationService.class, "证据回放查询必须装配");
    assertUnique(EvidenceSnapshotRepository.class, "SDK 必须为 EvidenceSnapshotRepository 生成实现");
    assertUnique(LlmUsageListener.class, "用量计量监听必须常驻装配");

    // 通知 / 集成（端口编排，纯内存兜底默认）
    assertUnique(AlertNotificationApplicationService.class, "告警通知应用服务必须装配");
    assertUnique(IntegrationApplicationService.class, "集成应用服务必须装配");
    assertUnique(IntegrationAuditPort.class, "集成审计端口必须装配（碰外部系统回调留痕）");

    // 确定性额度内核（碰钱，审计端口常驻）
    assertUnique(LimitLedger.class, "额度台账内核必须装配");
    assertUnique(LedgerAuditPort.class, "额度变异审计端口必须装配");

    // 内嵌 eval（生产拓扑独有）：EvalCaseRepository / EvalRunRepository 经 @EnableSqlRepositories 注册
    assertUnique(EvalManagementApplicationService.class, "评估管理应用服务必须装配（eval 仓储已注册）");
    assertUnique(EvalCaseRepository.class, "SDK 必须为 EvalCaseRepository 生成实现");
    assertUnique(EvalRunRepository.class, "SDK 必须为 EvalRunRepository 生成实现");

    // SDK 仓储基础设施（生产拓扑必须 @ComponentScan("com.bone.metadata.sdk") 才拿得到）：
    // 缺这些 Bean，SDK 的 RepositoryFactoryBean / columnAllocator / embeddedMetadataService 整链无法初始化 →
    // 启动失败。
    assertPresent(
        ColumnAllocationRepository.class,
        "SDK @Repository 必须经组件扫描拿到（生产拓扑扫 com.bone.metadata.sdk 的契约）");
    assertPresent(
        ExtensionCoordinator.class, "SDK @Component 必须经组件扫描拿到（缺它 RepositoryFactoryBean 初始化失败）");

    // 租户端口同名三连撞的回归护栏：gateway / rag / trace 各定义同简单名 TenantProvider 端口（不同包 = 不同类型），
    // Bean 名必须带前缀区分，否则「Bean 定义覆盖被禁」直接启动失败。任一被改回 tenantProvider 本断言先一步红。
    assertBeanByName("gatewayTenantProvider", "网关租户端口 Bean 名须带前缀（防同名覆盖）");
    assertBeanByName("ragTenantProvider", "RAG 租户端口 Bean 名须带前缀（防同名覆盖）");
    assertBeanByName("traceTenantProvider", "证据链租户端口 Bean 名须带前缀（防同名覆盖）");
  }

  @Test
  @DisplayName("装配自检：关键 Bean 真实实例化，未被延迟初始化占位掩盖问题")
  void keyBeansAreReallyInstantiated() {
    assertThat(context.getBean(OrchestrationApplicationService.class)).isNotNull();
    assertThat(context.getBean(RagApplicationService.class)).isNotNull();
    assertThat(context.getBean(IntegrationApplicationService.class)).isNotNull();
    assertThat(context.getBean(EvalManagementApplicationService.class)).isNotNull();
    assertThat(context.getBean(LimitLedger.class)).isNotNull();
    assertThat(context.getBean(EvidenceQueryApplicationService.class)).isNotNull();
  }

  private void assertUnique(Class<?> type, String description) {
    assertThat(context.getBeanNamesForType(type)).as(description).hasSize(1);
  }

  private void assertPresent(Class<?> type, String description) {
    assertThat(context.getBeanNamesForType(type)).as(description).isNotEmpty();
  }

  private void assertBeanByName(String name, String description) {
    assertThat(context.containsBean(name)).as(description).isTrue();
  }
}
