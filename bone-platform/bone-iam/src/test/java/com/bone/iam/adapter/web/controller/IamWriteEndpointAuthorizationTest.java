package com.bone.iam.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 写端点授权**负向**测试（2026-10-04 建立，对应设计诊断 P1-9）。
 *
 * <p><b>为什么必须有</b>：本模块 50 个测试类里断言 403/401 的**一处都没有**，而它是写端点最多（48）、 权限码最敏感（租户 / 会话 / 租户隔离）的模块。删掉某个
 * {@code @PreAuthorize}、把 {@code and} 改成 {@code or}、或 {@code @EnableMethodSecurity} 漏配，本模块测试**全绿**
 * —— 静态分析查得出「注解缺失」， 却查不出「注解在 HTTP 层是否真的被执行」。
 *
 * <p><b>为什么不用 MockMvc</b>：本模块既有的 MockMvc 测试都是 {@code standaloneSetup(controller)}， 它只装配 Controller
 * 本身、不挂安全过滤器链 ⇒ {@code @PreAuthorize} 在那样的上下文里**根本不执行**， 拿它断言授权只会得到永远为真的假绿灯。这里改用两条互补的运行期判据：
 *
 * <ol>
 *   <li><b>切面是否挂载</b>：断言容器里确实有方法安全 advisor，把「注解变死注解」这一整类问题堵死；
 *   <li><b>表达式是否真的拒绝</b>：调用 Spring Security 自身用来判定 {@code @PreAuthorize} 的 {@link
 *       PreAuthorizeAuthorizationManager} 来求值 —— 刻意**不**自己拼 SpEL， 这样「表达式写法变动 ⇒ 求值结果变化」这条链路才真的存在。
 * </ol>
 *
 * <p><b>覆盖率自证</b>：{@link #coverageIsComplete()} 断言容器内 {@code @RestController} 数量与受测清单 一致，避免有人新增
 * Controller 后本测试静默只剩老端点（参数集为空会让参数化测试**跳过而非失败**， 这类假绿灯比红灯危险）。
 *
 * <p><b>已知边界（如实记录，不做假绿灯）</b>：判据 ② 走的是「方法拦截」而非完整 HTTP 请求， 两者之间还隔着参数捕获与 filter 链的顺序；正因如此才必须配判据
 * ①，二者合起来才是闭环。 真正的端到端 HTTP 断言需要引入 {@code spring-security-test} 依赖（属 L3 改 pom），本次未动。
 */
@SpringBootTest
@ActiveProfiles("test")
class IamWriteEndpointAuthorizationTest {

  @Autowired private ApplicationContext context;

  /** 受测 Controller（与测试类同包，故无需 import；新增 Controller 必须同步来这里）。 */
  private static final List<Class<?>> UNDER_TEST =
      List.of(
          AccountController.class,
          AppController.class,
          AuditController.class,
          AuthController.class,
          DeptController.class,
          ExtensionE2eController.class,
          JwksController.class,
          MeController.class,
          MenuController.class,
          ModuleController.class,
          PermissionController.class,
          RoleController.class,
          SessionController.class,
          TenantController.class);

  /** 覆盖面自证：容器里实际暴露的 Controller 数量必须与受测清单一致。 */
  @Test
  void coverageIsComplete() {
    long inContainer = context.getBeanNamesForAnnotation(RestController.class).length;
    assertEquals(
        UNDER_TEST.size(),
        inContainer,
        "容器内 @RestController 数量("
            + inContainer
            + ") 与受测清单("
            + UNDER_TEST.size()
            + ") 不一致：新增或删除 Controller 时必须同步更新 UNDER_TEST，否则覆盖面会静默缩水");
  }

  /** 判据 ② 的兜底：写端点必须**都**带 @PreAuthorize，否则「删注解」只会让参数集变小而静默跳过。 */
  @Test
  void everyWriteEndpointCarriesAuthorization() {
    List<String> missing = new ArrayList<>();
    for (Class<?> controller : UNDER_TEST) {
      for (Method m : controller.getDeclaredMethods()) {
        String key = writeMappingKey(m);
        boolean exempt =
            baselineExemptions().contains(controller.getSimpleName() + ".java")
                || baselineExemptions().contains(controller.getSimpleName() + ".java#" + key);
        if (key != null && m.getAnnotation(PreAuthorize.class) == null && !exempt) {
          missing.add(controller.getSimpleName() + "." + m.getName());
        }
      }
    }
    assertTrue(
        missing.isEmpty(),
        "写端点缺 @PreAuthorize（任何登录用户即可写）：" + missing + "；注意这类缺口会让上面的参数化用例被跳过而非失败，所以必须单独断言");
  }

  /** 判据 ①：方法安全切面真的挂在容器里。 */
  @Test
  void methodSecurityAspectIsInstalled() {
    // 不按 6.x 各版本的具体类名去匹配：方法安全 advisor 的实现类在 6.0/6.2/6.5 之间换过名字，
    // 改判「容器内有没有方法安全 advisor」这个语义，才不会一升级依赖就假红。
    boolean installed =
        context.getBeansOfType(Advisor.class).values().stream()
            .anyMatch(
                advisor -> {
                  String name = advisor.getClass().getName();
                  return name.toLowerCase().contains("methodsecurity")
                      || name.contains("AuthorizationManager");
                });
    assertTrue(
        installed,
        "@EnableMethodSecurity 未生效（容器内无方法安全 advisor）⇒ 全仓 @PreAuthorize 瞬间"
            + "变成死注解；这类失效静态分析看不出来，只能由运行期断言兜住");
  }

  /** 判据 ②：所有写端点的授权表达式对「已认证、零权限码」的主体一律拒绝。 */
  @ParameterizedTest(name = "{0}")
  @MethodSource("writeEndpointCases")
  void writeEndpointDeniedWhenAuthenticatedWithoutAuthority(Method endpoint, String spel) {
    AuthorizationDecision decision = decide(endpoint, List.of(), false);
    assertFalse(
        decision.isGranted(),
        endpoint.getDeclaringClass().getSimpleName()
            + "."
            + endpoint.getName()
            + " 的授权表达式对『已登录但无权限码』的主体放行 ⇒ 任何登录用户都能写");
  }

  /**
   * 对照组：同一个表达式在「权限码齐 + 平台管理员」时必须放行。
   *
   * <p>没有这条，判据 ② 可以靠写死 {@code false} 而永远绿 —— 测试自己先得被证伪，才配谈守规则。
   */
  @ParameterizedTest(name = "对照 {0}")
  @MethodSource("writeEndpointCases")
  void authorizationExpressionIsNotTriviallyFalse(Method endpoint, String spel) {
    AuthorizationDecision decision = decide(endpoint, allAuthorities(), true);
    assertTrue(
        decision.isGranted(),
        endpoint.getDeclaringClass().getSimpleName()
            + "."
            + endpoint.getName()
            + " 的授权表达式即权限齐备也拒绝 ⇒ 表达式写错或判据本身失效");
  }

  /** 写映射判定（含 PUT/POST/DELETE）。 */
  private static boolean isWriteMapping(Method m) {
    return writeMappingKey(m) != null;
  }

  /**
   * 还原「HTTP 方法 + 路径」，即授权基线里 {@code exempt_methods} 的键格式（{@code POST:/change-password}）。
   *
   * <p>刻意与 {@code check-controller-authorization.py} 用同一套键：测试若自己另立口径，就会出现 「门禁认为合规豁免、测试却报缺失」的两套真相 ——
   * 那正是基线机制最忌讳的东西。
   */
  private static String writeMappingKey(Method m) {
    PostMapping post = m.getAnnotation(PostMapping.class);
    if (post != null) {
      return "POST:" + firstPath(post.value(), post.path());
    }
    PutMapping put = m.getAnnotation(PutMapping.class);
    if (put != null) {
      return "PUT:" + firstPath(put.value(), put.path());
    }
    DeleteMapping del = m.getAnnotation(DeleteMapping.class);
    if (del != null) {
      return "DELETE:" + firstPath(del.value(), del.path());
    }
    return null;
  }

  private static String firstPath(String[] value, String[] path) {
    String[] source = value.length > 0 ? value : path;
    return source.length == 0 ? "" : source[0];
  }

  /**
   * 读授权基线（单一真源），返回「文件名」与「文件名#方法:路径」两级豁免键。
   *
   * <p>基线读不到时**显式失败**而不是当作「无豁免」：静默当成无豁免会让一次路径变更直接变成 满屏假红，而静默当成全豁免则等于关掉判据 —— 两种都不可接受。
   */
  private static Set<String> baselineExemptions() {
    Path baseline = repoRoot().resolve("doc/architecture/controller-authorization-baseline.json");
    try (java.io.InputStream in = java.nio.file.Files.newInputStream(baseline)) {
      com.fasterxml.jackson.databind.JsonNode root =
          new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
      Set<String> keys = new java.util.LinkedHashSet<>();
      root.path("exempt").fieldNames().forEachRemaining(keys::add);
      root.path("exempt_methods").fieldNames().forEachRemaining(keys::add);
      return keys;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "读不到授权基线 " + baseline + "（cwd=" + System.getProperty("user.dir") + "）", e);
    }
  }

  /** 自 cwd 上溯找仓库根（surefire 的工作目录是模块目录，不是仓库根）。 */
  private static Path repoRoot() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null) {
      if (Files.isRegularFile(
          dir.resolve("doc/architecture/controller-authorization-baseline.json"))) {
        return dir;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("从 " + System.getProperty("user.dir") + " 上溯未找到仓库根");
  }

  /** 枚举受测写端点（显式清单，理由见类注释）。 */
  static Stream<Arguments> writeEndpointCases() {
    List<Arguments> out = new ArrayList<>();
    for (Class<?> controller : UNDER_TEST) {
      for (Method m : controller.getDeclaredMethods()) {
        if (!isWriteMapping(m)) {
          continue;
        }
        PreAuthorize auth = m.getAnnotation(PreAuthorize.class);
        if (auth != null) {
          out.add(Arguments.of(m, auth.value()));
        }
      }
    }
    return out.stream();
  }

  /**
   * 走 Spring Security 自己的判定入口求值，得到与运行期一致的结论。
   *
   * <p>{@code platformAccessGuard} 用**真实 bean**：它只看租户上下文是否为平台租户（0）， 所以这里把上下文摆成 0
   * 就等价于「平台管理员」，不需要任何替身 —— SpEL 里的 {@code @bean} 因此完全由真实容器解析， 与线上求值路径一致。
   */
  private AuthorizationDecision decide(
      Method method, List<String> authorities, boolean platformAdmin) {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "plain-user",
            "N/A",
            authorities.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList());
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    // 必须**显式**把容器交给 handler：manager.setApplicationContext() 只转发给内部 registry，
    // 不会给表达式处理器，结果是 SpEL 里 @bean 报 EL1057E「No bean resolver registered」
    // （负向组因 and 短路从不求值 @bean，刚好掩盖了这个问题 —— 对照组才暴露）。
    handler.setApplicationContext(context);
    PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();
    manager.setExpressionHandler(handler);
    if (platformAdmin) {
      TenantContext.setTenantId(0L);
    }
    try {
      return manager.check(() -> auth, invocationOf(method));
    } finally {
      TenantContext.clear();
    }
  }

  /** 求值时的 target 替身：授权表达式只用权限码与 @bean，不触碰真实实例。 */
  private static final Object TARGET_STUB = new Object();

  private static MethodInvocation invocationOf(Method method) {
    return new MethodInvocation() {
      @Override
      public Object getThis() {
        return TARGET_STUB;
      }

      @Override
      public AccessibleObject getStaticPart() {
        return method;
      }

      @Override
      public Method getMethod() {
        return method;
      }

      @Override
      public Object[] getArguments() {
        return new Object[0];
      }

      @Override
      public Object proceed() {
        throw new UnsupportedOperationException("授权判定已拒绝，不应执行业务方法");
      }
    };
  }

  /**
   * 对照组用的权限码全集：取自源码里所有 {@code hasAuthority('...')} 的实际取值 （共 20 个，覆盖 tenant / session / account /
   * role / apps / audit / depts / menus / permissions 与 extension 两码）。缺任何一个都会让对照组误判成「表达式写错」。
   */
  private static List<String> allAuthorities() {
    return List.of(
        "extension:plugins:write",
        "extension:points:write",
        "iam:accounts:read",
        "iam:accounts:write",
        "iam:apps:read",
        "iam:apps:write",
        "iam:audit:read",
        "iam:audit:write",
        "iam:depts:read",
        "iam:depts:write",
        "iam:menus:read",
        "iam:menus:write",
        "iam:permissions:read",
        "iam:permissions:write",
        "iam:roles:read",
        "iam:roles:write",
        "iam:sessions:read",
        "iam:sessions:write",
        "iam:tenants:read",
        "iam:tenants:write");
  }
}
