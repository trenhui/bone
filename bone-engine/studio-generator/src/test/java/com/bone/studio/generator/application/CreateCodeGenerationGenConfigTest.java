package com.bone.studio.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.FileGenerator;
import com.bone.studio.generator.infrastructure.service.AggregateTestGenerator;
import com.bone.studio.generator.infrastructure.service.ApiDocGenerator;
import com.bone.studio.generator.infrastructure.service.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 生成开关（includeTests / includeDocumentation）门禁。
 *
 * <p>这两个开关此前从前端一路传到后端却没人读：用户勾了「生成测试 / 生成文档」而产物里什么都没有。 物理库链路只有按开关追加这一条路径，不测住就会再次退化成摆设。
 */
class CreateCodeGenerationGenConfigTest {

  private final CreateCodeGenerationApplicationService service = service();

  @Test
  void defaultsToTestsOnDocsOffWhenGenConfigAbsent() {
    CreateCodeGenerationApplicationService.GenFlags flags = service.resolveGenFlags(null);
    assertTrue(flags.includeTests(), "未指定时应产出聚合单测（目标模块 TEST-HYGIENE-01 要求纯单测）");
    assertFalse(flags.includeDocumentation());
  }

  @Test
  void parsesFlagsFromGenConfigJson() {
    CreateCodeGenerationApplicationService.GenFlags flags =
        service.resolveGenFlags("{\"includeTests\":false,\"includeDocumentation\":true}");
    assertFalse(flags.includeTests());
    assertTrue(flags.includeDocumentation());
  }

  @Test
  void fallsBackToDefaultsWhenGenConfigIsBroken() {
    CreateCodeGenerationApplicationService.GenFlags flags = service.resolveGenFlags("{不是JSON");
    assertTrue(flags.includeTests(), "解析失败应按默认继续，不能让整单生成失败");
    assertFalse(flags.includeDocumentation());
  }

  @Test
  void physicalPathAppendsOptionalArtifactsByFlags() {
    List<GeneratedFile> out = new ArrayList<>();
    service.appendOptionalArtifacts(
        out, table(), command("{\"includeTests\":true,\"includeDocumentation\":true}"));

    assertEquals(2, out.size(), "两个开关都开时应追加单测 + 文档");
    assertTrue(
        out.stream().anyMatch(f -> f.getFilePath().endsWith("OrderTest.java")), out.toString());
    assertTrue(
        out.stream().anyMatch(f -> f.getFilePath().endsWith("order-api.md")), out.toString());
  }

  @Test
  void physicalPathSkipsOptionalArtifactsWhenFlagsOff() {
    List<GeneratedFile> out = new ArrayList<>();
    service.appendOptionalArtifacts(
        out, table(), command("{\"includeTests\":false,\"includeDocumentation\":false}"));

    assertTrue(out.isEmpty(), "开关关闭时不应产出附加文件");
  }

  private CreateCodeGenerationCommand command(String genConfig) {
    return CreateCodeGenerationCommand.builder()
        .basePackage("com.example")
        .moduleName("demo")
        .genConfig(genConfig)
        .build();
  }

  private GenTableMetadata table() {
    return GenTableMetadata.builder()
        .customEntityName("Order")
        .originalTableName("t_order")
        .tableComment("订单")
        .columns(
            List.of(
                GenColumnMetadata.builder().originalColumnName("id").javaType("Long").build(),
                GenColumnMetadata.builder()
                    .originalColumnName("customer_id")
                    .javaType("Long")
                    .isNullable(false)
                    .columnComment("客户ID")
                    .build()))
        .build();
  }

  private CreateCodeGenerationApplicationService service() {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
    cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
    TemplateRenderer renderer = new TemplateRenderer(cfg, new StringTemplateLoader());
    List<FileGenerator> generators =
        List.of(new AggregateTestGenerator(renderer), new ApiDocGenerator(renderer));
    // 其余依赖在本用例不涉及（只测开关解析与附加产物），传 null 保持用例聚焦
    return new CreateCodeGenerationApplicationService(
        null, null, null, null, null, null, generators, null, null, new ObjectMapper());
  }
}
