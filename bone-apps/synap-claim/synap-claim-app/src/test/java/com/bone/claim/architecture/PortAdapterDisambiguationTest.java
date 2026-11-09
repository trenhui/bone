package com.bone.claim.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 出站端口 ↔ 适配器的「唯一性 + 显式消歧」门禁（E-13.3 命名判据的补充）。
 *
 * <p><b>为什么不直接写「一个 Port 只能有一个实现」</b>：本模块 23 个出站端口里有 4 个是<b>多实现且合法</b>的—— {@code
 * DocumentParsePort}（LLM / 图片 / 文本层三条解析链）、{@code KnowledgeRetrievalPort}（本地桩 / 目录 / RAG）、 {@code
 * RuleEnginePort}（QlExpress / 内置）、{@code AiGatewayPort}（远端 / 本地桩）。它们靠
 * {@code @ConditionalOnProperty} 在不同开关下互斥激活，是<b>策略族</b>而不是歧义。把「唯一」写成字面量判据，
 * 上线第一天全红——而作者关掉一个门禁只需要一次误报。
 *
 * <p><b>真正的失败模式是「无条件实现出现两个」</b>：Spring 在注入点找不到唯一候选时抛 {@code
 * NoUniqueBeanDefinitionException}，应用<b>起不来</b>；这类问题既不过编译期，也不会被既有用例发现， 直到某个开关组合被打到。本仓已经踩过一次——{@code
 * LocalAiGatewayPortAdapter} 的类注释记着「两个 {@code @Primary} 时会抛
 * NoUniqueBeanDefinitionException，即本地模式开关一打开，应用就起不来」。
 *
 * <p>故判据是两条可判定规则，而不是「唯一」这个词：
 *
 * <ol>
 *   <li><b>每个 Port 至多一个无条件实现</b>（无 {@code @ConditionalOn*} / {@code @Profile} 之一）。多于一个 ⇒
 *       该端口在任何开关组合下都会歧义。
 *   <li><b>每个 Port 至多一个 {@code @Primary}</b>。两个 {@code @Primary} 与「没有唯一候选」等价，且更容易被 误认为「已经钦定了默认实现」。
 * </ol>
 *
 * <p><b>当日即绿、锁住未来违规</b>：现状满足——4 个多实现端口的无条件实现各只有一个 （{@code TextLayerDocumentParsePortAdapter}、{@code
 * AiGatewayPortAdapter}），{@code @Primary} 也各只有一个。
 *
 * <p><b>负向探针</b>：判定逻辑是纯函数，用合成输入验证「两个无条件必判红」「一个无条件 + 若干条件实现不误报」 「两个 {@code @Primary}
 * 必判红」。没有这组探针，判据本身写错（例如把 {@code @ConditionalOnProperty} 漏出 白名单，导致条件实现被当成无条件）仍会恒绿——那比没有门禁更危险。
 */
class PortAdapterDisambiguationTest {

  /** 出站端口包前缀（唯一真源：application.port.out）。 */
  private static final String PORT_PREFIX = "com.bone.claim.application.port.out.";

  /**
   * 激活条件注解的简单名白名单。
   *
   * <p>按注解名而非「是否存在 {@code @Conditional} 元注解」判定，是刻意选择：{@code @Conditional} 允许挂自定义 {@code Condition}
   * 实现做任意判断，用它当判据等于「有任意注解就算消歧」，会把真正的无条件实现放过去。 新增 {@code @ConditionalOn*}
   * 变体时须同步本集合，否则该实现会被判成无条件（负向探针守住这个行为）。
   */
  private static final Set<String> ACTIVATION_ANNOTATIONS =
      Set.of(
          "ConditionalOnProperty",
          "ConditionalOnBean",
          "ConditionalOnMissingBean",
          "ConditionalOnClass",
          "ConditionalOnMissingClass",
          "ConditionalOnExpression",
          "ConditionalOnResource",
          "ConditionalOnWebApplication",
          "ConditionalOnNotWebApplication",
          "ConditionalOnJava",
          "Profile",
          "Conditional");

  @Test
  @DisplayName("每个出站端口至多一个无条件实现（第二个会让容器启动期 NoUniqueBeanDefinitionException）")
  void at_most_one_unconditional_adapter_per_port() {
    Map<String, List<String>> violations = unconditionalViolations(scanModuleAdapters());

    assertThat(violations)
        .as(
            "以下端口有两个及以上无条件实现：注入点找不到唯一候选，应用会在启动期失败。"
                + "多实现须靠 @ConditionalOnProperty/@Profile 互斥激活，或只保留一个默认实现")
        .isEmpty();
  }

  @Test
  @DisplayName("每个出站端口至多一个 @Primary")
  void at_most_one_primary_per_port() {
    Map<String, List<String>> violations = primaryViolations(scanModuleAdapters());

    assertThat(violations).as("以下端口声明了多个 @Primary：两个 @Primary 与「无唯一候选」等价，同样在启动期失败").isEmpty();
  }

  @Test
  @DisplayName("扫描范围只含出站端口（domain 仓储由 SDK 代理，实现类不在本模块）")
  void scan_covers_only_outbound_ports() {
    assertThat(scanModuleAdapters().keySet())
        .isNotEmpty()
        .allSatisfy(port -> assertThat(port).startsWith(PORT_PREFIX).endsWith("Port"));
  }

  // ---------------------------------------------------------------- 负向探针

  @Test
  @DisplayName("探针：两个无条件实现必被判定（证明判据在跑，不是恒绿）")
  void probe_two_unconditional_is_detected() {
    Map<String, List<AdapterFacts>> ports = new LinkedHashMap<>();
    ports.put(
        "P",
        List.of(new AdapterFacts("A", Set.of(), false), new AdapterFacts("B", Set.of(), false)));

    assertThat(unconditionalViolations(ports))
        .containsOnlyKeys("P")
        .containsEntry("P", List.of("A", "B"));
  }

  @Test
  @DisplayName("探针：一个无条件 + 若干条件实现不误报（策略族是合法形态）")
  void probe_one_unconditional_plus_conditional_is_clean() {
    Map<String, List<AdapterFacts>> ports = new LinkedHashMap<>();
    ports.put(
        "P",
        List.of(
            new AdapterFacts("Default", Set.of("Component"), false),
            new AdapterFacts("Alt", Set.of("Component", "ConditionalOnProperty"), false),
            new AdapterFacts("Alt2", Set.of("Component", "Profile"), false)));

    assertThat(unconditionalViolations(ports)).isEmpty();
  }

  @Test
  @DisplayName("探针：两个 @Primary 必被判定")
  void probe_two_primary_is_detected() {
    Map<String, List<AdapterFacts>> ports = new LinkedHashMap<>();
    ports.put(
        "P",
        List.of(
            new AdapterFacts("A", Set.of("Component", "ConditionalOnProperty"), true),
            new AdapterFacts("B", Set.of("Component", "Profile"), true)));

    assertThat(primaryViolations(ports)).containsOnlyKeys("P");
  }

  @Test
  @DisplayName("探针：只剩条件实现时不算「有无条件实现」缺失（不误报 0 个）")
  void probe_all_conditional_is_clean() {
    Map<String, List<AdapterFacts>> ports = new LinkedHashMap<>();
    ports.put(
        "P",
        List.of(
            new AdapterFacts("A", Set.of("ConditionalOnProperty"), false),
            new AdapterFacts("B", Set.of("ConditionalOnProperty"), false)));

    assertThat(unconditionalViolations(ports)).isEmpty();
  }

  // ---------------------------------------------------------------- 判定逻辑（纯函数）

  /** 适配器事实：类名 + 全部注解简单名 + 是否 {@code @Primary}。 */
  record AdapterFacts(String name, Set<String> annotations, boolean primary) {}

  /**
   * 每个端口 → 其无条件实现名单；仅保留违规项（多于一个）。
   *
   * <p>键取端口简单名（报错信息可读）；端口全名在 {@link #scanModuleAdapters()} 里承载。
   */
  static Map<String, List<String>> unconditionalViolations(Map<String, List<AdapterFacts>> byPort) {
    return duplicatesByPort(
        filterNames(
            byPort, a -> a.annotations().stream().noneMatch(ACTIVATION_ANNOTATIONS::contains)));
  }

  /** 每个端口 → 其 {@code @Primary} 名单；仅保留违规项（多于一个）。 */
  static Map<String, List<String>> primaryViolations(Map<String, List<AdapterFacts>> byPort) {
    return duplicatesByPort(filterNames(byPort, AdapterFacts::primary));
  }

  private static Map<String, List<String>> filterNames(
      Map<String, List<AdapterFacts>> byPort, Predicate<AdapterFacts> keep) {
    Map<String, List<String>> out = new TreeMap<>();
    byPort.forEach(
        (port, adapters) -> {
          List<String> names = new ArrayList<>();
          for (AdapterFacts a : adapters) {
            if (keep.test(a)) {
              names.add(a.name());
            }
          }
          out.put(port, names);
        });
    return out;
  }

  private static Map<String, List<String>> duplicatesByPort(Map<String, List<String>> namesByPort) {
    Map<String, List<String>> violations = new TreeMap<>();
    namesByPort.forEach(
        (port, names) -> {
          if (names.size() > 1) {
            violations.put(port.substring(port.lastIndexOf('.') + 1), List.copyOf(names));
          }
        });
    return violations;
  }

  /** 扫描主源码：端口全名 → 该端口的实现类事实（排除接口 / 抽象类 / 枚举）。 */
  static Map<String, List<AdapterFacts>> scanModuleAdapters() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bone.claim");

    Map<String, List<AdapterFacts>> byPort = new TreeMap<>();
    for (JavaClass clazz : classes) {
      if (clazz.isInterface()
          || clazz.isEnum()
          || clazz.isAnonymousClass()
          || clazz.isLocalClass()
          || clazz.getModifiers().contains(JavaModifier.ABSTRACT)) {
        continue;
      }
      // 匿名 / 局部实现不是容器 Bean（典型：Port 自带的 NOOP 匿名实现），不参与消歧判定。
      Set<String> annotationNames = new HashSet<>();
      clazz.getAnnotations().forEach(a -> annotationNames.add(a.getRawType().getSimpleName()));

      for (JavaType type : clazz.getInterfaces()) {
        if (!(type instanceof JavaClass)) {
          continue;
        }
        JavaClass iface = (JavaClass) type;
        String ifaceName = iface.getName();
        if (ifaceName.startsWith(PORT_PREFIX) && ifaceName.endsWith("Port")) {
          byPort
              .computeIfAbsent(ifaceName, k -> new ArrayList<>())
              .add(
                  new AdapterFacts(
                      clazz.getSimpleName(), annotationNames, annotationNames.contains("Primary")));
        }
      }
    }
    return byPort;
  }
}
