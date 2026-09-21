package com.bone.metadata.sdk.sql.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.test.domain.User;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 通道的租户策略必须显式声明：缺 {@link TenantScope} 时启动期拒绝注册。
 *
 * <p>背景：注解默认值是 {@link TenantScopeMode#MANUAL}（不注入），"忘写注解"过去只会让 SQL 静默裸奔。
 * 本测试锁住把静默失败改成显式失败的那一步，以及接口级默认值 / 方法级覆盖的解析规则。
 */
class RepositoryFactoryBeanTenantScopeTest {

  /** 无任何租户策略声明：必须被拒绝。 */
  interface NoScope extends Repository<User, Long> {
    List<User> selectByName(String name);
  }

  /** 接口级默认策略 + 方法级覆盖。 */
  @TenantScope(TenantScopeMode.MANUAL)
  interface InterfaceScoped extends Repository<User, Long> {
    List<User> selectByName(String name);

    @TenantScope(TenantScopeMode.AUTO)
    List<User> selectByCode(String code);
  }

  /** 反向覆盖：接口级默认 AUTO，个别方法退回 MANUAL。 */
  @TenantScope(TenantScopeMode.AUTO)
  interface AutoDefaultManualMethod extends Repository<User, Long> {
    List<User> selectByName(String name);

    @TenantScope(TenantScopeMode.MANUAL)
    List<User> selectByLegacySql(String name);
  }

  @Test
  @DisplayName("缺 @TenantScope 的 SQL 方法 → 启动期抛异常，且报错点名方法与接口")
  void rejectsMethodWithoutTenantScope() throws Exception {
    Method method = NoScope.class.getMethod("selectByName", String.class);

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> RepositoryFactoryBean.requireTenantScope(NoScope.class, method));

    assertTrue(ex.getMessage().contains("@TenantScope"), ex.getMessage());
    assertTrue(ex.getMessage().contains("selectByName"), ex.getMessage());
    assertTrue(ex.getMessage().contains(NoScope.class.getName()), ex.getMessage());
  }

  @Test
  @DisplayName("接口级默认策略生效；方法级注解覆盖接口级")
  void resolvesInterfaceDefaultWithMethodOverride() throws Exception {
    Method inherited = InterfaceScoped.class.getMethod("selectByName", String.class);
    assertEquals(
        TenantScopeMode.MANUAL,
        RepositoryFactoryBean.requireTenantScope(InterfaceScoped.class, inherited).value());

    Method overridden = InterfaceScoped.class.getMethod("selectByCode", String.class);
    assertEquals(
        TenantScopeMode.AUTO,
        RepositoryFactoryBean.requireTenantScope(InterfaceScoped.class, overridden).value());
  }

  @Test
  @DisplayName("接口级 AUTO 默认值时，方法级 MANUAL 仍优先生效")
  void methodLevelWinsOverAutoDefault() throws Exception {
    Method inherited = AutoDefaultManualMethod.class.getMethod("selectByName", String.class);
    assertEquals(
        TenantScopeMode.AUTO,
        RepositoryFactoryBean.requireTenantScope(AutoDefaultManualMethod.class, inherited).value());

    Method overridden = AutoDefaultManualMethod.class.getMethod("selectByLegacySql", String.class);
    assertEquals(
        TenantScopeMode.MANUAL,
        RepositoryFactoryBean.requireTenantScope(AutoDefaultManualMethod.class, overridden)
            .value());
  }
}
