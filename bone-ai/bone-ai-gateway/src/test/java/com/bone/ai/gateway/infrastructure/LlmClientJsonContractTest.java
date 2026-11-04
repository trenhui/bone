package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmStreamEvent;
import com.bone.ai.gateway.infrastructure.security.JsonOutputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code LlmClient} 实现的生产契约测试（G0-2）。
 *
 * <p>依据 Google Rules of ML #5「独立于机器学习测试基础设施」、#10「警惕静默失败」，以及《The ML Test
 * Score》基础设施类。契约只有一条但必须对所有实现成立：
 *
 * <blockquote>
 *
 * {@code jsonResponse=true ⇒ LlmResponse.content() 是裸 JSON 对象}
 *
 * </blockquote>
 *
 * <p>修复前：默认实现 {@link MockLlmClient} 恒返回 {@code "MOCK[model]: ..."} 自然语言， 与网关 {@link
 * JsonOutputValidator} 及下游业务消费方的解析契约冲突，导致未配 provider 时 AI 节点 100% 降级且原因不可定位。
 * 本测试把「测试替身违反生产契约」从运行时偶发失败变成 CI 判红的静态约束。
 *
 * <p>契约覆盖本身也受守护：新增 {@code LlmClient} 实现若未加入 {@link #COVERED_IMPLEMENTATIONS} 并补契约断言，{@link
 * #allImplementationsAreCoveredByContract()} 会判红——避免契约测试随实现增长而静默失效 （ML Test Score：测试体系自身必须可测）。
 */
class LlmClientJsonContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String INFRASTRUCTURE_PACKAGE = "com.bone.ai.gateway.infrastructure";

  /** 已被本契约测试覆盖的实现；新增实现必须同步登记并补断言。 */
  private static final Set<String> COVERED_IMPLEMENTATIONS =
      Set.of("MockLlmClient", "OpenAiCompatibleClient", "FallbackLlmClient", "CascadeLlmClient");

  private static final LlmRequest JSON_REQUEST =
      new LlmRequest(
          "m", List.of(LlmMessage.user("hello")), null, null, true, false, "t-contract", null);

  private static final LlmRequest TEXT_REQUEST = LlmRequest.of("m", List.of(LlmMessage.user("世界")));

  @Test
  @DisplayName("契约守护：所有 LlmClient 实现都被覆盖，新增实现不得静默漏测")
  void allImplementationsAreCoveredByContract() throws Exception {
    Set<String> discovered = discoverLlmClientImplementations();

    // 双向守护：登记项必须真实存在（防删实现留空名），磁盘上的实现必须全部登记（防新增漏测）
    assertThat(discovered).isNotEmpty().containsAll(COVERED_IMPLEMENTATIONS);
    assertThat(discovered).isSubsetOf(COVERED_IMPLEMENTATIONS);
  }

  @Test
  @DisplayName("契约：jsonResponse=true 时默认实现返回裸 JSON 并自报替身身份")
  void mockHonoursJsonContractAndSelfIdentifies() throws Exception {
    LlmResponse resp = new MockLlmClient().chat(JSON_REQUEST);

    var node = MAPPER.readTree(resp.content());
    assertThat(node.isObject()).isTrue();
    assertThat(node.path("mock").asBoolean(false)).isTrue();
    assertThat(node.path("echo").asText()).isEqualTo("hello");
  }

  @Test
  @DisplayName("契约：网关输出校验器接受该响应，证明契约有执行者而非仅文档约定")
  void validatorAcceptsMockJsonResponse() {
    String content = new MockLlmClient().chat(JSON_REQUEST).content();

    assertThatCode(() -> new JsonOutputValidator().validate(content, "t-contract"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("契约护栏：自然语言响应在结构化模式下必须被判红")
  void validatorRejectsNaturalLanguage() {
    assertThatThrownBy(() -> new JsonOutputValidator().validate("MOCK[m]: hello", "t-contract"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("契约：jsonResponse=false 保持自然语言回显，不破坏既有调用点")
  void mockKeepsPlainTextWhenJsonNotRequested() {
    assertThat(new MockLlmClient().chat(TEXT_REQUEST).content()).isEqualTo("MOCK[m]: 世界");
  }

  @Test
  @DisplayName("契约：真实 OpenAI 兼容客户端的结构化输出同样可解析")
  void openAiCompatibleClientHonoursJsonContract() throws Exception {
    String raw =
        MAPPER.writeValueAsString(
            Map.of(
                "choices",
                List.of(
                    Map.of("message", Map.of("content", "{\"ok\":true}"), "finish_reason", "stop")),
                "model",
                "m",
                "usage",
                Map.of()));
    LlmHttpTransport transport = (url, headers, body) -> raw;
    LlmResponse resp =
        new OpenAiCompatibleClient(transport, "http://localhost", null, "m").chat(JSON_REQUEST);

    var node = MAPPER.readTree(resp.content());
    assertThat(node.isObject()).isTrue();
    assertThat(node.path("ok").asBoolean(false)).isTrue();
    assertThatCode(() -> new JsonOutputValidator().validate(resp.content(), "t-contract"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("契约传递：降备援装饰器在主档位失败后仍返回结构化响应")
  void fallbackDecoratorPreservesJsonContractWhenPrimaryFails() throws Exception {
    LlmClient failing =
        req -> {
          throw new IllegalStateException("primary down");
        };
    LlmResponse resp =
        new FallbackLlmClient(failing, new MockLlmClient(), "backup").chat(JSON_REQUEST);

    assertThat(MAPPER.readTree(resp.content()).isObject()).isTrue();
    assertThatCode(() -> new JsonOutputValidator().validate(resp.content(), "t-contract"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("契约传递：级联升档装饰器不改写主档位的结构化响应")
  void cascadeDecoratorPreservesJsonContract() throws Exception {
    LlmResponse resp =
        new CascadeLlmClient(new MockLlmClient(), new MockLlmClient(), "strong", 0.9, null)
            .chat(JSON_REQUEST);

    var node = MAPPER.readTree(resp.content());
    assertThat(node.isObject()).isTrue();
    assertThat(node.path("mock").asBoolean(false)).isTrue();
  }

  @Test
  @DisplayName("契约：默认流式实现委托 chat，也必须遵守 JSON 契约")
  void defaultStreamChatHonoursJsonContract() throws Exception {
    List<String> tokens = new ArrayList<>();
    new MockLlmClient()
        .streamChat(
            JSON_REQUEST,
            event -> {
              if (event instanceof LlmStreamEvent.Token token) {
                tokens.add(token.delta());
              }
            });

    assertThat(tokens).hasSize(1);
    assertThat(MAPPER.readTree(tokens.get(0)).isObject()).isTrue();
    assertThat(MAPPER.readTree(tokens.get(0)).path("mock").asBoolean(false)).isTrue();
  }

  /** 扫描编译输出目录，找出本包下所有 {@link LlmClient} 实现（不依赖 Spring / Reflections）。 */
  private static Set<String> discoverLlmClientImplementations() throws Exception {
    Enumeration<URL> resources =
        LlmClientJsonContractTest.class
            .getClassLoader()
            .getResources(INFRASTRUCTURE_PACKAGE.replace('.', '/'));
    Set<String> names = new LinkedHashSet<>();
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      if (!"file".equals(url.getProtocol())) {
        continue;
      }
      File dir = new File(url.toURI());
      File[] classFiles = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
      if (classFiles == null) {
        continue;
      }
      for (File file : classFiles) {
        String simple = file.getName().replace(".class", "");
        Class<?> type = Class.forName(INFRASTRUCTURE_PACKAGE + "." + simple);
        if (LlmClient.class.isAssignableFrom(type) && !type.isInterface()) {
          names.add(simple);
        }
      }
    }
    return names;
  }
}
