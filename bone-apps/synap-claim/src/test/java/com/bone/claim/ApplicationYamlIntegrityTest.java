package com.bone.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

/**
 * application.yml 的启动期可解析性（2026-11-08 实盘事故的回归钉子）。
 *
 * <p><b>事故</b>：给 AI 网关补配置时，在 {@code bone:} 下又写了一个 {@code ai:}，与既有块同名。snakeyaml 对重复键 直接抛 {@code
 * found duplicate key ai}，<b>服务启动即失败</b>；而 CI 只跑不加载 Spring 上下文的单测（唯一的 {@code @SpringBootTest} 是
 * E2E，靠 {@code E2E_LOCAL} 开关在 CI 里跳过），于是全绿。
 *
 * <p><b>为什么用 {@link YamlPropertySourceLoader} 而不是自己解析</b>：它与 Spring Boot 启动同款、同一份 {@code
 * OriginTrackedYamlLoader}，重复键策略不会在"测试通过、上线炸"之间漂移；而且不需要 DB、不需要上下文， 能进普通单测跑到 PR 阶段。
 *
 * <p>第二条用例负责证明第一条不是恒真——同一个加载器对重复键确实会判错。缺了它，"文件能打开"和"键没重复" 就是两件无法区分的事。
 */
class ApplicationYamlIntegrityTest {

  @Test
  @DisplayName("application.yml 可被生产同款加载器解析（重复键 / 语法错都会让服务起不来）")
  void applicationYmlLoadsWithProductionLoader() throws IOException {
    List<PropertySource<?>> loaded =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));

    assertThat(loaded).as("application.yml 未解析出任何属性源").isNotEmpty();
    PropertySource<?> source = loaded.get(0);
    assertThat(source.containsProperty("bone.ai.gateway.base-url"))
        .as("LlmGatewayProperties 绑定的 bone.ai.gateway.* 必须可解析（改名/挪位置都会静默失效）")
        .isTrue();
  }

  @Test
  @DisplayName("同一加载器对重复键判错（证明上一条不是恒真）")
  void loaderRejectsDuplicateKeys(@TempDir Path dir) throws IOException {
    Path duplicated = dir.resolve("duplicate-key.yml");
    String yaml =
        Stream.of(
                "bone:",
                "  ai:",
                "    gateway:",
                "      model: a",
                "  ai:",
                "    gateway:",
                "      model: b",
                "")
            .collect(Collectors.joining("\n"));
    Files.writeString(duplicated, yaml);

    assertThatThrownBy(
            () -> new YamlPropertySourceLoader().load("dup", new FileSystemResource(duplicated)))
        .as("加载器若不拒重复键，上一条用例就只是「文件能打开」")
        .hasMessageContaining("duplicate key");
  }
}
