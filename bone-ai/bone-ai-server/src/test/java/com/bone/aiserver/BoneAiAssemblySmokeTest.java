package com.bone.aiserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.aiserver.claim.ClaimService;
import com.bone.aiserver.web.AiPipelineController;
import com.bone.aiserver.web.ClaimController;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.EvidenceTraceListener;
import com.bone.aitrace.infrastructure.event.LlmUsageListener;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.extension.repository.ColumnAllocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * <b>装配级冒烟测试</b>：把 {@link BoneAiApplication} 的<strong>全量 Spring 上下文真实装配一次</strong>。
 *
 * <p><b>补的是什么洞</b>：bone-ai 此前只有「纯单测（mock 一切 / 手工 new 依赖链）」一档，装配层完全没验。 而这条链既深又满是条件注解：
 * {@code @EnableSqlRepositories} → SDK 仓储 → {@code @ConditionalOnBean} 治理服务 → 编排 → 控制器。 这类故障的共同特征是
 * <strong>编译通过、单测全绿、一启动就炸</strong>——真实案例：{@code ClaimController} 依赖 {@link ClaimService}，
 * 而平台库按「只提供端口、不拥有业务流程」不会替产品域注册它，缺了 {@link ClaimServiceConfiguration} 参考宿主根本起不来， 却因没有任何上下文测试而一直没暴露。
 *
 * <p><b>为何用 H2 而不是本地 MySQL</b>：本测试验的是「能否装配起来」，与数据库方言无关；H2 进程内数据源无需 Docker 与镜像拉取， 离线 / CI 无网 / 开发机默认
 * {@code mvn test} 都能跑。{@code spring.sql.init.mode=never} 保证装配阶段不执行任何 DDL/DML， SDK
 * 只做类路径上的元数据注册；真实持久化语义仍由集成测试覆盖。
 *
 * <p><b>范式来源</b>：与 {@code bone-blueprint} 的 {@code BlueprintAssemblySmokeTest} 同一模式（同类洞的第二次踩坑）。
 * bone-ai 无 {@code @Scheduled}（库形态刻意不加调度）、无注册中心依赖，故无需中和 cron 与 discovery。
 */
@SpringBootTest(
    classes = BoneAiApplication.class,
    properties = {
      // 进程内数据源：装配验证不触碰真实库（DDL 真源仍是仓库 DDL，此处刻意不建表）
      "spring.datasource.url=jdbc:h2:mem:bone_ai_assembly;DB_CLOSE_DELAY=-1;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      // 进程内 H2 的 sa 账号默认口令为空，故此处刻意不显式声明口令：写成空值会被密钥扫描
      // 判为「硬编码口令」而阻断提交（空口令不含任何凭据信息，属扫描器误报的真源）。
      "spring.sql.init.mode=never"
    })
class BoneAiAssemblySmokeTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("全量上下文可装配：控制器 / 应用服务 / 端口实现 / SDK 仓储均唯一且可解析")
  void contextAssemblesAllLayers() {
    // 入站：两个 REST 控制器必须可解析（覆盖 controller → application 的注入链；ClaimService 缺 Bean 曾在此炸）
    assertUnique(
        ClaimController.class, "ClaimController 必须唯一装配（ClaimServiceConfiguration 缺失会在此暴露）");
    assertUnique(AiPipelineController.class, "AiPipelineController 必须唯一装配");

    // 产品域流程：平台不替产品域注册它的 Bean，参考宿主必须自己接（这是「怎么接」的活模板）
    assertUnique(ClaimService.class, "ClaimService 必须由参考宿主装配（平台库不拥有业务流程）");

    // 编排与治理：条件链 @EnableSqlRepositories → repos → @ConditionalOnBean 服务
    assertUnique(OrchestrationApplicationService.class, "编排执行器必须装配（规则治理 + LLM 网关就绪）");
    assertUnique(RuleApplicationService.class, "规则治理服务必须装配（RuleAsset 仓储已注册）");
    assertUnique(RuleQueryApplicationService.class, "规则查询服务必须装配");
    assertUnique(HitlApplicationService.class, "HITL 人工终审服务必须装配（hitl 仓储已注册）");
    assertUnique(LlmGatewayApplicationService.class, "LLM 网关门面必须装配");
    assertUnique(LlmClient.class, "LLM 客户端必须唯一（默认 MockLlmClient）");
    assertUnique(DocumentParser.class, "文档解析器必须唯一（默认 PDFBox）");

    // 证据链：监听 / 回放依赖 EvidenceSnapshotRepository，计量消费常驻
    assertUnique(EvidenceTraceListener.class, "证据监听必须装配（EvidenceSnapshotRepository 已注册）");
    assertUnique(EvidenceQueryApplicationService.class, "证据回放查询必须装配");
    assertUnique(LlmUsageListener.class, "用量计量监听必须常驻装配");

    // 确定性额度内核：碰钱内核默认必须留痕（审计端口常驻）
    assertUnique(LimitLedger.class, "额度台账内核必须装配");
    assertUnique(LedgerAuditPort.class, "额度变异审计端口必须装配（碰钱内核不留痕不可接受）");

    // SDK 动态生成的仓储：端口无实现 / 扫描范围缺失会在此暴露
    assertUnique(
        HitlItemRepository.class, "SDK 必须为 HitlItemRepository 生成实现（@EnableSqlRepositories 生效）");
    assertUnique(RuleAssetRepository.class, "SDK 必须为 RuleAssetRepository 生成实现");
    assertUnique(EvidenceSnapshotRepository.class, "SDK 必须为 EvidenceSnapshotRepository 生成实现");

    // SDK 仓储基础设施（参考宿主必须 @ComponentScan("com.bone.metadata.sdk") 才拿得到）：
    // 缺这些 Bean，SDK 的 RepositoryFactoryBean / columnAllocator / embeddedMetadataService 整链无法初始化 →
    // 启动失败。
    // 此处把「扫描契约」变成回归护栏——谁把扫描去掉，启动错误会又深又绕，但本断言先一步红。
    assertPresent(
        ColumnAllocationRepository.class,
        "SDK @Repository 必须经组件扫描拿到（参考宿主扫 com.bone.metadata.sdk 的契约）");
    assertPresent(
        ExtensionCoordinator.class, "SDK @Component 必须经组件扫描拿到（缺它 RepositoryFactoryBean 初始化失败）");
  }

  @Test
  @DisplayName("装配自检：关键 Bean 真实实例化，未被延迟初始化占位掩盖问题")
  void keyBeansAreReallyInstantiated() {
    // getBean 触发实际创建：构造注入缺失依赖 / 循环引用在此暴露，懒加载占位骗不过去
    assertThat(context.getBean(ClaimController.class)).isNotNull();
    assertThat(context.getBean(ClaimService.class)).isNotNull();
    assertThat(context.getBean(OrchestrationApplicationService.class)).isNotNull();
    assertThat(context.getBean(HitlApplicationService.class)).isNotNull();
    assertThat(context.getBean(LimitLedger.class)).isNotNull();
    assertThat(context.getBean(EvidenceTraceListener.class)).isNotNull();
  }

  private void assertUnique(Class<?> type, String description) {
    assertThat(context.getBeanNamesForType(type)).as(description).hasSize(1);
  }

  private void assertPresent(Class<?> type, String description) {
    assertThat(context.getBeanNamesForType(type)).as(description).isNotEmpty();
  }
}
