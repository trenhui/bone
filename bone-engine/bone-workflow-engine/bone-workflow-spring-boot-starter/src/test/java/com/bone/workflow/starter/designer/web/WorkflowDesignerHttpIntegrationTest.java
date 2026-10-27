package com.bone.workflow.starter.designer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.designer.WorkflowDesignerAccessDeniedException;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 设计器 REST 层的<b>真实 HTTP 联调测试</b>：内嵌 Tomcat（随机端口）+ 内嵌 H2，全程走 {@link HttpClient} 与 {@link
 * ObjectMapper}，不经过 MockMvc。
 *
 * <p><b>为什么要这一层</b>：{@link WorkflowDesignerControllerTest} 用 standalone MockMvc，绕过了真实
 * 容器与消息转换器协商——它能证明「方法签名对得上」，但证明不了「axios 发过来的这串东西能被接住」。真实联调里出过的 事故（缺 {@code Content-Type} 触发
 * 415、body 里混入换行触发 400、{@code @JsonInclude(NON_NULL)} 让 {@code data} 字段整体消失）都只在这一层现形。本类把这些失败模式钉成回归。
 *
 * <p><b>覆盖的三方契约</b>
 *
 * <ul>
 *   <li>传输层：路径、方法、{@code Content-Type}、{@code X-Operator-Id} 审计头、UTF-8 中文往返；
 *   <li>语义层：草稿可脏 / 发布 fail-closed / revision 乐观并发 / 版本上下线；
 *   <li>错误层：400（缺租户、超长、空 body）、403（授权拒绝）、404（版本不存在）、409（revision 过期）、422（发布违规）。
 * </ul>
 *
 * <p><b>宿主职责的显式化</b>：生产里租户上下文由宿主的过滤器注入、授权由宿主的 {@link WorkflowDesignerAuthorizer}
 * 提供。本类用最小实现把这两条宿主责任摆出来（{@code X-Tenant-Id} 头 / {@code X-Test-Deny} 头），既是联调夹具，也是接线示例——starter
 * 自身不内置任何身份来源。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("工作流设计器 REST · 真实 HTTP 联调")
class WorkflowDesignerHttpIntegrationTest {

  private static final String DEF_KEY = "desig_demo";
  private static final String TENANT = "1";
  private static final String OPERATOR = "u-designer";

  /**
   * 显式 HTTP/1.1。
   *
   * <p>不写这一行会<b>整条链路挂死 30 秒后超时</b>：{@link HttpClient} 默认 HTTP/2，明文请求会先发 {@code Upgrade: h2c}
   * 升级探测；内嵌 Tomcat 未配置 h2c 时既不完成升级也不回落到 1.1，请求永远停在连接层——Tomcat 工作线程全空闲（已用线程栈实证）， 服务端一条日志都不留，极难定位。浏览器
   * / axios 不发这种探测，所以这是测试客户端才有的坑。
   */
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private static final ObjectMapper JSON = new ObjectMapper();

  private static ConfigurableApplicationContext app;
  private static String base;

  // ============================== 启动 / 关闭 ==============================

  @BeforeAll
  static void boot() throws IOException {
    // 必须在**非临时端口段**里挑端口，不能用 server.port=0。踩坑记录：本机跑着若干占用临时端口段
    // （macOS 默认 49152-65535）的本地服务；0 端口拿到 53709 后，Tomcat 绑 0.0.0.0:53709 成功，
    // 而那个服务占着 127.0.0.1:53709 —— 于是「连到 127.0.0.1」的请求全被那个服务接走，回了一个
    // 与本项目无关的 401 AUTH_REQUIRED。现象极迷惑：Tomcat 日志显示端口就绪、工作线程全空闲、
    // 客户端却收到不属于自己的响应。故固定在 18xxx 段探测，避开临时端口段。
    int port = freePort();
    app =
        new SpringApplicationBuilder(WebConfig.class)
            .web(WebApplicationType.SERVLET)
            .run(
                "--server.port=" + port,
                "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
                // 设计器只依赖「设计态写路径 + 试运行」，不依赖运行期引擎；用宿主显式开关关掉 EngineConfiguration，
                // 免得本测试被与设计器无关的端口装配问题拖住（开关是官方出口，不是绕过缺陷）。
                "--bone.workflow.engine.enabled=false",
                // 缺省 authorizer 是 fail-closed；本测试提供自己的实现（见 WebConfig#testAuthorizer），
                // 因此这里无需 allow-anonymous 开关——顺带证明「宿主 bean 覆盖缺省」这条接线路径可用。
                "--bone.workflow.designer.allow-anonymous=false",
                // 端口必须走命令行参数（优先级高于环境变量与 jar 内 application.yml），
                // 用 SpringApplicationBuilder.properties() 只是 defaultProperties，优先级最低，实测会被压掉。
                "--management.server.port=" + (port + 1));
    String boundPort = app.getEnvironment().getProperty("local.server.port");
    assertEquals(String.valueOf(port), boundPort, "Tomcat 实际绑定端口应与请求端口一致（否则请求会被别的服务接走）");
    base = "http://127.0.0.1:" + boundPort + "/api/v1/workflow/definitions";
  }

  /**
   * 在 18080 起向上找一个「此刻确实空闲」的端口。
   *
   * <p>刻意不用 {@code new ServerSocket(0)}：内核会把临时端口段（49152+）分给我们，而那一段正是本地各种 中间服务的地盘（见 {@link #boot()}
   * 的踩坑记录）。
   */
  private static int freePort() throws IOException {
    for (int candidate = 18080; candidate < 18180; candidate++) {
      try (java.net.ServerSocket socket =
          new java.net.ServerSocket(candidate, 0, java.net.InetAddress.getLoopbackAddress())) {
        socket.setReuseAddress(false);
        return socket.getLocalPort();
      } catch (IOException busy) {
        // 该端口被占，试下一个
      }
    }
    throw new IllegalStateException("18080-18179 全被占用，测试无法启动内嵌容器");
  }

  @AfterAll
  static void shutdown() {
    if (app != null) {
      app.close();
    }
  }

  /**
   * 最小可运行的「宿主」装配。
   *
   * <p>刻意保留 {@code @EnableAutoConfiguration}：设计器的两条自动装配（引擎、Web 层）必须经 {@code
   * AutoConfiguration.imports} 真实加载，否则「条件装配写错导致 bean 不注册」这类缺陷在本测试里就看不见了。 只排除跟测试无关的 JDBC
   * 自动配置（本上下文的数据源由 {@link WorkflowSdkTestContext} 提供）。
   */
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        // 测试类路径经 SDK 传递带进了 oauth2-resource-server：它会给请求链挂 BearerTokenAuthenticationFilter，
        // 没有 token 的联调请求一律 401（且响应体是项目自定义信封，不是框架默认的空体，极易误判成业务鉴权）。
        // 设计器端点的鉴权由 WorkflowDesignerAuthorizer 负责，认证是宿主的职责，故此处关掉资源服务器装配。
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
      })
  @Import(WorkflowSdkTestContext.WorkflowSdkTestConfig.class)
  static class WebConfig {

    /** 宿主职责之一：把身份上下文灌进 {@link TenantContext}（生产里由租户解析过滤器完成）。 */
    @Bean
    FilterRegistrationBean<Filter> tenantContextFilter() {
      Filter filter =
          (request, response, chain) -> {
            try {
              String tenant = ((HttpServletRequest) request).getHeader("X-Tenant-Id");
              TenantContext.setTenantId(tenant == null || tenant.isBlank() ? null : tenant);
              chain.doFilter(request, response);
            } finally {
              // 必须清：Tomcat 线程复用，残留租户上下文会让下一个请求读到别人的数据。
              TenantContext.clear();
            }
          };
      FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
      registration.addUrlPatterns("/api/*");
      registration.setOrder(1);
      return registration;
    }

    /**
     * 宿主职责之三：<b>安全链</b>。
     *
     * <p>测试类路径经 SDK 传递带进了 {@code bone-security} 与 oauth2-resource-server，缺省的链要求 Bearer
     * token，联调请求会整片 401。这里提供一条放行的链——注意这正是<b>宿主必须做的事</b>：设计器端点永远位于宿主认证链之后， {@code X-Operator-Id}
     * 只是审计维度，<b>不是</b>认证凭据（它能被任意调用方伪造，拦不住未认证访问）。
     */
    @Bean
    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
      http.csrf(AbstractHttpConfigurer::disable)
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .httpBasic(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable);
      return http.build();
    }

    /**
     * 宿主职责之二：判权。缺省 bean 是 fail-closed（一律 403），这里提供覆盖实现——{@code @ConditionalOnMissingBean}
     * 保证它优先生效，同时验证这条覆盖路径真的通。
     *
     * <p>{@code X-Test-Deny} 头存在的请求一律拒绝，用来把 403 分支走通；其余放行。
     */
    @Bean
    WorkflowDesignerAuthorizer testAuthorizer() {
      return (action, defKey, operatorId) -> {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String deny = attributes == null ? null : attributes.getRequest().getHeader("X-Test-Deny");
        if (deny != null && !deny.isBlank()) {
          throw new WorkflowDesignerAccessDeniedException(action, defKey, operatorId);
        }
      };
    }
  }

  // ============================== 用例 ==============================

  @Test
  @Order(1)
  @DisplayName("校验端点：合法 / 半成品 / 空 body / 超长 / 授权拒绝 / 缺租户")
  void validateContract() throws Exception {
    String valid = fixture("valid-definition.json");

    Res ok = call("POST", "/validate", valid, tenantHeaders());
    assertEquals(200, ok.status(), "合法定义应校验通过：" + ok.raw());
    assertTrue(ok.body().path("data").path("valid").asBoolean(), "valid 字段应为 true");
    assertEquals(0, ok.body().path("data").path("violations").size(), "合法定义不应有违规");

    Res dirty = call("POST", "/validate", fixture("dirty-definition.json"), tenantHeaders());
    assertEquals(200, dirty.status(), "半成品校验仍应 200（校验不是发布）：" + dirty.raw());
    assertFalse(dirty.body().path("data").path("valid").asBoolean());
    JsonNode violations = dirty.body().path("data").path("violations");
    assertTrue(violations.size() >= 3, "半成品应报出缺 END / 断头 / 缺 roleCode 至少 3 条：" + violations);
    assertTrue(violations.toString().contains("WF-003"), "应报缺结束节点 WF-003：" + violations);
    assertTrue(violations.toString().contains("WF-020"), "应报指派参数缺失 WF-020：" + violations);
    // 违规必须带 locator，前端才能按节点高亮——靠解析 message 文本反推节点是明令禁止的。
    assertTrue(
        violations.toString().contains("\"locator\":\"mgr\""), "节点级违规应带 locator=mgr：" + violations);

    assertEquals(400, call("POST", "/validate", "", tenantHeaders()).status(), "空 body 应 400");

    String huge = "{\"key\":\"" + "x".repeat(1 << 20) + "\"}";
    Res oversize = call("POST", "/validate", huge, tenantHeaders());
    assertEquals(400, oversize.status(), "超过 1 Mi 字符应 400（反序列化前拒绝）：" + oversize.raw());
    assertTrue(oversize.body().path("message").asText().contains("超过上限"), oversize.raw());

    Res denied = call("POST", "/validate", valid, headers("X-Test-Deny", "1"));
    assertEquals(403, denied.status(), "授权拒绝应 403：" + denied.raw());
    assertEquals("READ", denied.body().path("data").path("action").asText(), "403 应带被拒动作");

    // 校验是纯函数（不落库、不绑租户），缺租户也应当能跑通——这是设计器「边改边校验」的前提。
    Res noTenantValidate = call("POST", "/validate", valid, Map.of());
    assertEquals(200, noTenantValidate.status(), "校验不应依赖租户上下文：" + noTenantValidate.raw());

    // 真正绑租户的是查询 / 写路径：宿主没接租户过滤器时必须响亮失败（400），而不是查出一个空结果。
    Res noTenantList = call("GET", "", null, Map.of());
    assertEquals(400, noTenantList.status(), "缺租户上下文的查询应 400（宿主未接过滤器）：" + noTenantList.raw());
  }

  @Test
  @Order(2)
  @DisplayName("全生命周期：草稿 → 发布 → 版本 → 上下线 → 试运行 → 丢弃")
  void fullLifecycleOverRealHttp() throws Exception {
    String valid = fixture("valid-definition.json");
    String dirty = fixture("dirty-definition.json");

    // ---- 初始：无草稿、无版本 ----
    assertEquals(0, call("GET", "", null, tenantHeaders()).body().path("data").size(), "初始应无已发布定义");
    Res noDraft = call("GET", "/" + DEF_KEY + "/draft", null, tenantHeaders());
    assertEquals(200, noDraft.status());
    assertTrue(
        noDraft.body().path("data").isMissingNode(),
        "无草稿时 data 应整体缺省（@JsonInclude(NON_NULL)），前端用 res.data == null 判新建：" + noDraft.raw());

    // ---- 写路径必须有操作人 ----
    assertEquals(
        400,
        call("POST", "/" + DEF_KEY + "/draft", draftBody(valid, null), tenantHeaders()).status(),
        "缺 X-Operator-Id 应 400（拒绝匿名写入审计列）");

    // ---- 首次保存草稿 ----
    Res saved = call("POST", "/" + DEF_KEY + "/draft", draftBody(valid, null), operatorHeaders());
    assertEquals(200, saved.status(), "首存草稿应成功：" + saved.raw());
    assertEquals(
        1, saved.body().path("data").path("draft").path("revision").asInt(), "首存 revision=1");
    assertEquals(OPERATOR, saved.body().path("data").path("draft").path("updatedBy").asText());

    // ---- 草稿可脏：半成品允许暂存，但违规必须随响应返回 ----
    Res dirtyDraft = call("POST", "/" + DEF_KEY + "/draft", draftBody(dirty, 1), operatorHeaders());
    assertEquals(200, dirtyDraft.status(), "草稿允许暂存半成品：" + dirtyDraft.raw());
    assertEquals(2, dirtyDraft.body().path("data").path("draft").path("revision").asInt());
    assertTrue(
        dirtyDraft.body().path("data").path("violations").size() >= 3, "保存半成品应随响应返回违规清单，供画布实时高亮");

    // ---- 发布 fail-closed：脏草稿必须 422 且带结构化违规 ----
    Res rejected = call("POST", "/" + DEF_KEY + "/publish", publishBody(2), operatorHeaders());
    assertEquals(422, rejected.status(), "脏草稿发布必须 422：" + rejected.raw());
    assertTrue(
        rejected.body().path("data").path("violations").size() >= 3,
        "422 应带 data.violations 供前端逐节点高亮：" + rejected.raw());

    // ---- 乐观并发：过期 revision 必须 409，不能覆盖别人的草稿 ----
    Res stale = call("POST", "/" + DEF_KEY + "/draft", draftBody(valid, 0), operatorHeaders());
    assertEquals(409, stale.status(), "revision 过期应 409：" + stale.raw());
    assertTrue(stale.body().path("message").asText().contains("revision"), stale.raw());

    // ---- 修好再存 → 发布 ----
    Res fixed = call("POST", "/" + DEF_KEY + "/draft", draftBody(valid, 2), operatorHeaders());
    assertEquals(200, fixed.status(), fixed.raw());
    assertEquals(3, fixed.body().path("data").path("draft").path("revision").asInt());

    Res published = call("POST", "/" + DEF_KEY + "/publish", publishBody(3), operatorHeaders());
    assertEquals(200, published.status(), "干净草稿应发布成功：" + published.raw());
    assertEquals(1, published.body().path("data").path("version").asInt(), "首个版本 version=1");
    assertEquals(
        "费用报销（设计器联调）", published.body().path("data").path("defName").asText(), "UTF-8 中文定义名应原样往返");

    // ---- 发布后草稿被清空（前端据此退出「草稿中」状态） ----
    Res cleared = call("GET", "/" + DEF_KEY + "/draft", null, tenantHeaders());
    assertEquals(200, cleared.status());
    assertTrue(cleared.body().path("data").isMissingNode(), "发布后应无草稿：" + cleared.raw());

    // ---- 版本查询：列表 / 单版本（含 ui 段原文）/ 不存在 ----
    Res versions = call("GET", "/" + DEF_KEY + "/versions", null, tenantHeaders());
    assertEquals(200, versions.status());
    assertEquals(1, versions.body().path("data").size(), "应恰有 1 个版本");

    Res one = call("GET", "/" + DEF_KEY + "/versions/1", null, tenantHeaders());
    assertEquals(200, one.status());
    String storedJson = one.body().path("data").path("definitionJson").asText();
    assertTrue(storedJson.contains("\"ui\""), "定义原文必须保留 ui 段（版式还原依赖它）");
    assertTrue(storedJson.contains("\"flowDoc\""), "ui.flowDoc 必须无损存储（避免图→树有损反推）");
    assertTrue(storedJson.contains("金额判断"), "节点中文标签应原样保存（UTF-8 不走 \\u 转义）");

    Res missing = call("GET", "/" + DEF_KEY + "/versions/99", null, tenantHeaders());
    assertEquals(404, missing.status(), "不存在的版本应 404：" + missing.raw());
    assertEquals(404, missing.body().path("code").asInt(), "404 信封 code 应与 HTTP 状态一致");

    // ---- 总览 + 上下线 ----
    // 两个口径必须分开：
    //   GET /                运行期口径，只含「已上线」的 key（运行期按 key 取定义，绝不能取到已下线版本）；
    //   GET /?includeOffline=true  设计器口径，含已下线 key —— 否则下线一个流程后它从列表消失，
    //                        设计器再也没有入口把它重新上线（联调实测到的死路）。
    Res latest = call("GET", "", null, tenantHeaders());
    assertEquals(1, latest.body().path("data").size(), "总览应有 1 个 key：" + latest.raw());
    assertTrue(latest.body().path("data").get(0).path("enabled").asBoolean(), "新发布默认上线");

    Res offline =
        call("POST", "/" + DEF_KEY + "/enabled", enabledBody(1, false), operatorHeaders());
    assertEquals(200, offline.status(), offline.raw());
    assertFalse(offline.body().path("data").path("enabled").asBoolean(), "下线后 enabled=false");

    Res afterOfflineRuntime = call("GET", "", null, tenantHeaders());
    assertEquals(
        0,
        afterOfflineRuntime.body().path("data").size(),
        "运行期口径不应再返回已下线的定义：" + afterOfflineRuntime.raw());

    Res afterOfflineDesigner = call("GET", "?includeOffline=true", null, tenantHeaders());
    assertEquals(
        1,
        afterOfflineDesigner.body().path("data").size(),
        "设计器口径必须仍能看到已下线的 key，否则无法重新上线：" + afterOfflineDesigner.raw());
    assertFalse(
        afterOfflineDesigner.body().path("data").get(0).path("enabled").asBoolean(),
        "设计器口径应如实反映下线状态");

    Res backOnline =
        call("POST", "/" + DEF_KEY + "/enabled", enabledBody(1, true), operatorHeaders());
    assertTrue(backOnline.body().path("data").path("enabled").asBoolean());
    assertEquals(
        1, call("GET", "", null, tenantHeaders()).body().path("data").size(), "重新上线后应回到运行期口径");

    // ---- 试运行：走大额分支 / 走默认分支 ----
    Res big =
        call("POST", "/simulate", simulateBody(valid, Map.of("amount", 50000)), operatorHeaders());
    assertEquals(200, big.status(), "试运行应成功：" + big.raw());
    assertEquals(
        "[\"start\",\"cond\",\"cfo\",\"end\"]",
        big.body().path("data").path("path").toString(),
        "金额 5 万应走大额分支到财务总监：" + big.raw());
    // 分支判定必须可解释：命中哪条、默认边是否被兜底，都要能在响应里读到（发布前排错靠它）。
    JsonNode traces = big.body().path("data").path("conditionTraces");
    assertEquals(1, traces.size(), "应给出 1 个条件节点的判定明细：" + big.raw());
    assertTrue(traces.get(0).path("branches").get(0).path("matched").asBoolean(), "大额分支应命中");
    assertFalse(
        traces.get(0).path("branches").get(1).path("defaulted").asBoolean() == false, "默认边应保留兜底标记");

    Res small =
        call("POST", "/simulate", simulateBody(valid, Map.of("amount", 100)), operatorHeaders());
    assertEquals(
        "[\"start\",\"cond\",\"mgr\",\"end\"]",
        small.body().path("data").path("path").toString(),
        "金额 100 应走默认分支到直属主管：" + small.raw());

    // ---- 丢弃草稿（幂等：无草稿也 200） ----
    assertEquals(
        200,
        call("DELETE", "/" + DEF_KEY + "/draft", null, operatorHeaders()).status(),
        "丢弃草稿应 200");
    assertEquals(
        200, call("DELETE", "/" + DEF_KEY + "/draft", null, operatorHeaders()).status(), "重复丢弃应幂等");
  }

  @Test
  @Order(3)
  @DisplayName("授权分档：写路径 / 发布 / 上下线各自可被单独拒绝")
  void authorizationIsPerAction() throws Exception {
    String valid = fixture("valid-definition.json");

    assertEquals(
        403,
        call("POST", "/" + DEF_KEY + "/draft", draftBody(valid, null), denyWriteHeaders()).status(),
        "保存草稿应受 WRITE 档位保护");
    assertEquals(
        403,
        call("POST", "/" + DEF_KEY + "/publish", publishBody(null), denyWriteHeaders()).status(),
        "发布应受 PUBLISH 档位保护");
    assertEquals(
        403,
        call("POST", "/" + DEF_KEY + "/enabled", enabledBody(1, true), denyWriteHeaders()).status(),
        "上下线应受 ENABLE 档位保护");
    assertEquals(
        403, call("GET", "", null, headers("X-Test-Deny", "1")).status(), "读路径应受 READ 档位保护");
  }

  // ============================== HTTP 夹具 ==============================

  private record Res(int status, String contentType, JsonNode body, String raw) {}

  private static Res call(String method, String path, String body, Map<String, String> extraHeaders)
      throws Exception {
    Map<String, String> headers = new LinkedHashMap<>();
    if (body != null) {
      headers.put("Content-Type", "application/json");
    }
    headers.putAll(extraHeaders);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    headers.forEach(builder::header);
    HttpResponse<String> response =
        HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    return new Res(
        response.statusCode(),
        contentType,
        response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body()),
        response.body());
  }

  private static Map<String, String> tenantHeaders() {
    return Map.of("X-Tenant-Id", TENANT);
  }

  private static Map<String, String> operatorHeaders() {
    return Map.of("X-Tenant-Id", TENANT, "X-Operator-Id", OPERATOR);
  }

  /** 带审计头与拒绝头：写端点的 {@code X-Operator-Id} 由 Spring 在进方法前解析，缺了直接 400，轮不到判权。 */
  private static Map<String, String> denyWriteHeaders() {
    return Map.of("X-Tenant-Id", TENANT, "X-Operator-Id", OPERATOR, "X-Test-Deny", "1");
  }

  private static Map<String, String> headers(String name, String value) {
    return Map.of("X-Tenant-Id", TENANT, name, value);
  }

  private static String draftBody(String definitionJson, Integer expectedRevision)
      throws Exception {
    ObjectNode node = JSON.createObjectNode().put("definitionJson", definitionJson);
    if (expectedRevision == null) {
      node.putNull("expectedRevision");
    } else {
      node.put("expectedRevision", expectedRevision);
    }
    return JSON.writeValueAsString(node);
  }

  private static String publishBody(Integer expectedRevision) throws Exception {
    ObjectNode node = JSON.createObjectNode();
    if (expectedRevision == null) {
      node.putNull("expectedRevision");
    } else {
      node.put("expectedRevision", expectedRevision);
    }
    return JSON.writeValueAsString(node);
  }

  private static String enabledBody(int version, boolean enabled) throws Exception {
    return JSON.writeValueAsString(
        JSON.createObjectNode().put("version", version).put("enabled", enabled));
  }

  private static String simulateBody(String definitionJson, Map<String, Object> sampleData)
      throws Exception {
    ObjectNode node = JSON.createObjectNode();
    node.put("definitionJson", definitionJson);
    node.set("sampleData", JSON.valueToTree(sampleData));
    node.put("initiatorId", OPERATOR);
    return JSON.writeValueAsString(node);
  }

  private static String fixture(String name) throws IOException {
    try (InputStream in =
        WorkflowDesignerHttpIntegrationTest.class.getResourceAsStream("/designer/" + name)) {
      assertNotNull(in, "缺少夹具 /designer/" + name);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
