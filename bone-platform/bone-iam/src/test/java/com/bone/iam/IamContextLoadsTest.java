package com.bone.iam;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.iam.application.AuthApplicationService;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.infrastructure.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * 容器级装配测试（MVP-10 质量下沉 ①）。
 *
 * <p><b>为什么必须有</b>：纯单测不加载 Spring 容器，Bean 缺失 / 循环依赖 / 自动配置失效在 {@code mvn test}
 * 里一律不暴露，只能等真起服务才炸（本模块曾因装配缺失而在运行期才显现）。 静态架构规则同样证明不了装配，所以这条只能由 {@code @SpringBootTest} 兜住（G-1）。
 *
 * <p><b>profile=test</b>：数据源为 H2 内存库（见 {@code src/test/resources/application-test.yml}）， 不依赖本地
 * MySQL，可在 CI 直接跑。
 */
@SpringBootTest
@ActiveProfiles("test")
class IamContextLoadsTest {

  @Autowired private ApplicationContext context;

  @Autowired private Environment environment;

  /** 容器能起，且登录主链路的关键 Bean 都在（应用服务入口 / 应用服务 / 域仓储 / 认证过滤器）。 */
  @Test
  void criticalBeansArePresent() {
    assertNotNull(context.getBean(AuthApplicationService.class), "登录应用服务未装配");
    assertNotNull(context.getBean(AccountRepository.class), "AccountRepository 未装配（SDK 仓储未扫描到）");
    assertNotNull(context.getBean(JwtAuthenticationFilter.class), "JWT 认证过滤器未装配");
  }

  /**
   * 配置键契约（MVP-10 质量下沉 ②）：{@code ${key:默认}} 里的键名写错会静默回落默认值，无报错无日志， 表现是「验签失败」这类离故障点很远的症状。这里锁死 IAM
   * 的关键配置键确实有值。
   */
  @Test
  void criticalConfigKeysAreBound() {
    String secret = environment.getProperty("bone.iam.jwt.secret-key");
    assertNotNull(secret, "bone.iam.jwt.secret-key 未绑定：与 bone-core JwtConfig 的键名不一致会静默回落");
    assertTrue(secret.length() >= 32, "JWT 密钥长度不足 32，jjwt 验签会直接失败");

    assertNotNull(environment.getProperty("spring.datasource.url"), "spring.datasource.url 未绑定");
  }

  /** 登录入口的跨租户查找必须真的可用（ADR-0029 失败关闭下的关键通道）。 */
  @Test
  void accountRepositoryExposesLoginLookup() {
    AccountRepository repository = context.getBean(AccountRepository.class);
    assertDoesNotThrow(
        () -> repository.findByUsernameForLoginAllTenants("no-such-user"),
        "findByUsernameForLoginAllTenants 在无租户上下文时抛出：登录会 500（ADR-0029 失败关闭回归）");
  }
}
