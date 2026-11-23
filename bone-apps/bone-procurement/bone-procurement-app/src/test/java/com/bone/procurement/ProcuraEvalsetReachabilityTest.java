package com.bone.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** evalset 可追溯性守卫：testClass 必须落在源码中，testSymbol 必须能在该测试类内命中。 */
class ProcuraEvalsetReachabilityTest {

  private static final Path REPO_ROOT = Path.of("..", "..", "..").toAbsolutePath().normalize();
  private static final Path EVALSET =
      REPO_ROOT.resolve("doc/ai-product/procuraAI/eval/ai-decision-evalset.json");
  private static final Path PROCUREMENT_ROOT = REPO_ROOT.resolve("bone-apps/bone-procurement");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void 所有评测测试类与符号在源码中可达() throws IOException {
    JsonNode root = MAPPER.readTree(EVALSET.toFile());
    Map<String, Set<String>> expected = new LinkedHashMap<>();
    for (JsonNode testCase : root.path("cases")) {
      String testClass = testCase.path("testClass").asText("");
      String testSymbol = testCase.path("testSymbol").asText("");
      if (!testClass.isBlank() && !testSymbol.isBlank()) {
        expected
            .computeIfAbsent(testClass, ignored -> new java.util.LinkedHashSet<>())
            .add(testSymbol);
      }
    }
    assertThat(expected).as("evalset 不得无 testClass/testSymbol").isNotEmpty();

    List<SourceFile> sources;
    try (Stream<Path> paths = Files.walk(PROCUREMENT_ROOT)) {
      sources =
          paths
              .filter(p -> p.toString().endsWith("Test.java") && Files.isRegularFile(p))
              .map(
                  p -> {
                    try {
                      return new SourceFile(p, Files.readString(p));
                    } catch (IOException e) {
                      throw new IllegalStateException("读取测试源码失败: " + p, e);
                    }
                  })
              .toList();
    }

    for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
      String testClass = entry.getKey();
      List<SourceFile> matches =
          sources.stream().filter(s -> s.content().contains("class " + testClass)).toList();
      assertThat(matches).as("evalset testClass %s 未在源码中找到", testClass).hasSize(1);
      String content = matches.get(0).content();
      for (String symbol : entry.getValue()) {
        assertThat(content).as("%s 的 testSymbol %s 未在测试源码中命中", testClass, symbol).contains(symbol);
      }
    }
  }

  private record SourceFile(Path path, String content) {}
}
