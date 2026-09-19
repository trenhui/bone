package com.bone.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.blueprint.adapter.schedule.OrderOutboxRelayJob;
import com.bone.blueprint.adapter.web.controller.OrderController;
import com.bone.blueprint.adapter.web.controller.PaymentController;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.PaymentApplicationService;
import com.bone.blueprint.application.port.out.OrderOutboxPort;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

/**
 * <b>装配级冒烟测试</b>：把 {@link BoneBlueprintApplication} 的<strong>全量 Spring 上下文真实装配一次</strong>。
 *
 * <p><b>补的是什么洞</b>：此前本模块只有「纯单测（mock 一切）」与「集成测试（需本地 MySQL，且被 {@code integration}
 * 标签排除在默认构建之外）」两档，中间缺一档——<strong>装配层</strong>。装配层错误（bean 名冲突导致 {@code
 * NoUniqueBeanDefinitionException}、构造注入缺失依赖、循环引用、{@code @ConfigurationProperties}
 * 前缀写错导致占位符失效、组件扫描范围变化导致端口无实现）在纯单测里永远看不到， 而集成测试又因为依赖真实库而默认不跑。
 * 这类故障的共同特征是：<strong>编译通过、单测全绿、一启动就炸</strong>。
 *
 * <p><b>为何用 H2 而不是 Testcontainers / 本地 MySQL</b>：本测试要验的是「能否装配起来」，与数据库方言无关；H2 是进程内数据源， 无需 Docker
 * 守护进程与镜像拉取，所以在离线环境、CI 无网环境、以及开发机的默认 {@code mvn test} 里都能跑。 真实持久化语义（SDK 写路径、租户上下文、事务传播）仍由 {@code
 * -Pintegration} 下的本地 MySQL 集成测试覆盖——两者职责不重叠。
 *
 * <p><b>为何不需要建表</b>：{@code spring.sql.init.mode=never}，装配阶段不执行任何 DDL/DML，SDK 只做类路径上的元数据注册。
 */
@SpringBootTest(
    classes = BoneBlueprintApplication.class,
    properties = {
      // 进程内数据源：装配验证不触碰真实库（DDL 真源仍是仓库根 bone-init.sql，此处刻意不建表）
      "spring.datasource.url=jdbc:h2:mem:blueprint_assembly;DB_CLOSE_DELAY=-1;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      // 中和注册中心：装配测试不依赖 Nacos
      "spring.cloud.discovery.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.discovery.register-enabled=false",
      // 三个全租户扫描任务用 Spring 的 "-"（CRON_DISABLED）禁用：本测试不起调度语义
      "bone.blueprint.schedule.cancel-expired-orders-cron=-",
      "bone.blueprint.schedule.close-expired-payments-cron=-",
      "bone.blueprint.schedule.payment-inconsistency-check-cron=-",
      // 覆盖默认弱密钥，避免启动告警；本测试不验签，取值仅需 >= 32 字节
      "bone.iam.jwt.secret-key=blueprint-assembly-only-secret-key-min-32-bytes-long"
    })
class BlueprintAssemblySmokeTest {

  @Autowired private ApplicationContext context;

  /**
   * 摘掉 Outbox 中继任务：它用 {@code fixedDelay}（非 cron），无法用 {@code "-"} 关闭，且启动后<strong>立即执行首次</strong>。
   * 本测试不建表，它会因 {@code bp_outbox} 不存在而抛 {@code BadSqlGrammarException}——异常被任务吞掉、不影响装配结论，
   * 但会在日志里留下一整段堆栈，掩盖真正需要看的装配问题（与集成测试 {@code EventTenantContextIntegrationTest} 同处理）。
   */
  @MockBean private OrderOutboxRelayJob orderOutboxRelayJob;

  @Test
  @DisplayName("全量上下文可装配：入站控制器、应用服务、出站端口实现均唯一且可解析")
  void contextAssemblesAllLayers() {
    // 入站：两个 REST 控制器必须可被解析（覆盖 controller → application 的注入链）
    assertThat(context.getBeanNamesForType(OrderController.class))
        .as("OrderController 必须唯一装配（bean 名冲突会在此暴露）")
        .hasSize(1);
    assertThat(context.getBeanNamesForType(PaymentController.class))
        .as("PaymentController 必须唯一装配")
        .hasSize(1);

    // 应用服务：入站默认边界
    assertThat(context.getBeanNamesForType(OrderApplicationService.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(PaymentApplicationService.class)).hasSize(1);

    // 领域仓储：SDK 必须为两个聚合接口生成实现（端口无实现 / 扫描范围缺失会在此暴露）
    assertThat(context.getBeanNamesForType(OrderRepository.class))
        .as("SDK 必须能为 OrderRepository 生成实现（元数据注册失败会在此暴露）")
        .hasSize(1);
    assertThat(context.getBeanNamesForType(PaymentRepository.class)).hasSize(1);

    // 出站端口：application/port/out 声明的能力必须有 infrastructure 实现
    assertThat(context.getBeanNamesForType(OrderOutboxPort.class))
        .as("OrderOutboxPort 必须有且仅有一个基础设施实现")
        .hasSize(1);
  }

  @Test
  @DisplayName("装配自检：上下文无失败/循环依赖，且未回退到延迟初始化掩盖问题")
  void contextHasNoBrokenBeanDefinitions() {
    // 上下文能启动本身就是强断言，这里再确认关键 bean 不是「懒加载占位」而是真实实例化：
    // getBean 会触发实际创建，构造注入缺失依赖 / 循环引用在此暴露。
    assertThat(context.getBean(OrderController.class)).isNotNull();
    assertThat(context.getBean(OrderOutboxPort.class)).isNotNull();
    assertThat(context.getBean(OrderRepository.class)).isNotNull();
  }
}
