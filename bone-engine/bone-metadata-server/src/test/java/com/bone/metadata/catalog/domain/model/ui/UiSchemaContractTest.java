package com.bone.metadata.catalog.domain.model.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 契约单源反向断言（技术方案 §2.5）。
 *
 * <p>服务端白名单常量必须与 classpath 的 {@code ui/bone-ui-schema.schema.json} 的 {@code enum} 逐项一致 ——schema
 * 加类型而服务端测试不变红，就说明"先改契约再改代码"的顺序被打破了。 P0 不引入 validator 依赖，手写白名单 + 本测试即单源纪律的机器化载体。
 */
class UiSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonNode schemaRoot() throws Exception {
    try (InputStream in =
        Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream("ui/bone-ui-schema.schema.json")) {
      if (in == null) {
        throw new IllegalStateException("classpath 缺 ui/bone-ui-schema.schema.json（构建期须从 doc 同步）");
      }
      return MAPPER.readTree(in);
    }
  }

  private Set<String> enumOf(JsonNode root, String... path) {
    JsonNode node = root;
    for (String segment : path) {
      node = node.path(segment);
    }
    Set<String> out = new HashSet<>();
    if (node.isArray()) {
      for (JsonNode item : node) {
        if (item.isTextual()) {
          out.add(item.asText());
        }
      }
    }
    return out;
  }

  @Test
  void controlKinds_shouldMatchSchemaEnum() throws Exception {
    JsonNode root = schemaRoot();
    assertEquals(
        enumOf(root, "definitions", "control", "properties", "controlKind", "enum"),
        UiSchemaDef.CONTROL_KINDS);
  }

  @Test
  void conditionOps_shouldMatchSchemaEnum() throws Exception {
    JsonNode root = schemaRoot();
    assertEquals(
        enumOf(root, "definitions", "condition", "properties", "operator", "enum"),
        UiSchemaDef.CONDITION_OPS);
  }

  @Test
  void submitFunctions_shouldMatchSchemaEnum() throws Exception {
    JsonNode root = schemaRoot();
    assertEquals(
        enumOf(root, "definitions", "submitRule", "properties", "function", "enum"),
        UiSchemaDef.SUBMIT_FUNCTIONS);
  }
}
