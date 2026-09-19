package com.bone.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 配置键契约测试：源码里引用的 {@code ${bone.*}} 占位符键，必须在 {@code application*.yml} 里有定义。
 *
 * <p><b>为什么需要它</b>：本模块的占位符一律带默认值（{@code ${key:默认}}）。键名写错时 Spring <strong>不会报错</strong>，而是
 * 静默回落到默认值——配置"看起来写了"却完全不生效，且没有任何日志。这类错误在单元测试（mock 注入）、ArchUnit 与 spotless 里都不可见，只有本测试能拦住。
 *
 * <p><b>实例（本测试的第一个捕获）</b>：{@code PaymentController} 曾读 {@code
 * bone.payment.callback.allowed-source-ips}， 而 yml 定义的是 {@code
 * bone.blueprint.payment.callback.allowed-source-ips}——差一个 {@code blueprint} 段。后果是支付回调 <strong>来源
 * IP 白名单永久失效</strong>（回落到默认空串＝不限制来源），而 yml 里明明写着白名单。
 *
 * <p><b>只做单向全量断言</b>：「已定义 ⇒ 被引用」只对 {@code bone.blueprint.schedule.*} 做。其余 {@code bone.*} 段由
 * {@code @ConfigurationProperties} 的 binder 消费（如 {@code OrderOutboxProperties}），yml 里没有对应占位符，
 * 全量反向断言会全是假阳性。
 */
class ConfigKeysContractTest {

  private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
  private static final Path RESOURCE_ROOT = Path.of("src", "main", "resources");

  /** 只守护本模块自有前缀：{@code ${BONE_DB_PASSWORD}} 这类环境变量占位符不在范围内。 */
  private static final String GUARDED_PREFIX = "bone.";

  /** {@code ${key}} 或 {@code ${key:默认值}}；默认值里可能含 {@code :} 或 {@code *}，故取到第一个未转义的 {@code }}. */
  private static final Pattern PLACEHOLDER =
      Pattern.compile("\\$\\{\\s*([A-Za-z0-9_.\\-]+)\\s*(?::[^}]*)?\\}");

  /**
   * 引用的键必须已定义——否则因占位符带默认值而静默回落。
   *
   * <p>扫描范围：{@code src/main/java} 的 {@code *.java}（{@code @Value} / {@code @Scheduled} 占位符） 与
   * {@code src/main/resources} 的 {@code *.yml}（yml 内部的占位符引用）。
   */
  @Test
  @DisplayName("源码引用的 bone.* 占位符键必须在 application*.yml 中定义（防静默回落）")
  void referencedKeysMustBeDefined() throws IOException {
    requireSourceLayout();

    Set<String> referenced = referencedKeys();
    Set<String> defined = definedKeys();

    assertThat(referenced).isNotEmpty().as("扫描不到任何 %s* 占位符，扫描逻辑本身失效", GUARDED_PREFIX);
    assertThat(referenced)
        .allSatisfy(
            key ->
                assertThat(defined)
                    .as("占位符键 %s 未在任何 application*.yml 中定义（会静默回落到默认值）", key)
                    .contains(key));
  }

  /** {@code bone.blueprint.schedule.*} 的定义键必须被引用——防止留下无人消费的死配置（写了却不生效，运维以为已调过）。 */
  @Test
  @DisplayName("bone.blueprint.schedule.* 的定义键必须被源码引用（防死配置）")
  void scheduleKeysMustBeReferenced() throws IOException {
    requireSourceLayout();

    Set<String> referenced = referencedKeys();
    Set<String> scheduleDefined = new TreeSet<>();
    for (String key : definedKeys()) {
      if (key.startsWith("bone.blueprint.schedule.")) {
        scheduleDefined.add(key);
      }
    }

    assertThat(scheduleDefined).isNotEmpty().as("yml 中没有 bone.blueprint.schedule.* 段，扫描逻辑失效");
    assertThat(scheduleDefined)
        .allSatisfy(
            key -> assertThat(referenced).as("yml 定义的 %s 没有任何源码引用（死配置）", key).contains(key));
  }

  // ===== 扫描 =====

  private Set<String> referencedKeys() throws IOException {
    Set<String> keys = new TreeSet<>();
    for (Path file : sourceFiles()) {
      collectPlaceholders(Files.readString(file, StandardCharsets.UTF_8), keys);
    }
    return keys;
  }

  private void collectPlaceholders(String text, Set<String> sink) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    while (matcher.find()) {
      String key = matcher.group(1);
      if (key.startsWith(GUARDED_PREFIX)) {
        sink.add(key);
      }
    }
  }

  private List<Path> sourceFiles() throws IOException {
    try (Stream<Path> java = Files.walk(SOURCE_ROOT);
        Stream<Path> yml = Files.walk(RESOURCE_ROOT)) {
      List<Path> files = new ArrayList<>();
      java.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
      yml.filter(p -> p.toString().endsWith(".yml")).forEach(files::add);
      return files;
    }
  }

  // ===== yml 定义键 =====

  private Set<String> definedKeys() throws IOException {
    Set<String> keys = new TreeSet<>();
    List<Path> ymls;
    try (Stream<Path> stream = Files.walk(RESOURCE_ROOT)) {
      ymls =
          stream
              .filter(p -> p.getFileName().toString().startsWith("application"))
              .filter(p -> p.toString().endsWith(".yml"))
              .toList();
    }
    for (Path yml : ymls) {
      try (InputStream in = Files.newInputStream(yml)) {
        Object loaded = new Yaml().load(in);
        if (loaded instanceof Map<?, ?> map) {
          flatten(map, "", keys);
        }
      }
    }
    return keys;
  }

  @SuppressWarnings("unchecked")
  private void flatten(Map<?, ?> node, String prefix, Set<String> sink) {
    for (Map.Entry<?, ?> entry : node.entrySet()) {
      String key =
          prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> child) {
        flatten(child, key, sink);
      } else if (!(value instanceof List<?>)) {
        // 列表项无法用点式键表达（也不参与占位符解析），只登记标量键
        sink.add(key);
      }
    }
  }

  /**
   * 本测试是<strong>源码契约</strong>检查，依赖源码在工作目录中可见（Maven surefire 的工作目录即模块根目录）。 从 jar / 别的目录运行时源码不可见 ⇒
   * 跳过而不是误报失败；CI 与本机 {@code mvn test} 均在模块根运行。
   */
  private void requireSourceLayout() {
    Assumptions.assumeTrue(
        Files.isDirectory(SOURCE_ROOT) && Files.isDirectory(RESOURCE_ROOT),
        "源码目录不可见（非模块根工作目录），跳过配置键契约检查");
  }
}
