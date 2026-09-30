package com.bone.studio.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.bone.core.exception.NotFoundException;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateDetailQuery;
import com.bone.studio.generator.domain.gateway.BuiltInTemplateGateway;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.infrastructure.gateway.BuiltInTemplateGatewayAdapter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 内置模板回落：预览/校验必须与生成链路同样把 classpath 模板当真源。
 *
 * <p>此前两链口径不一致——{@code content} 为空时生成能跑（回落 classpath），预览却返回 null、校验判为无效模板。
 */
class BuiltInTemplateFallbackTest {

  private final CodeTemplateRepository repository = mock(CodeTemplateRepository.class);
  private final BuiltInTemplateGateway gateway = new BuiltInTemplateGatewayAdapter();
  private final ValidateTemplateApplicationService validate =
      new ValidateTemplateApplicationService(repository, gateway);
  private final PreviewTemplateApplicationService preview =
      new PreviewTemplateApplicationService(repository, gateway);

  @Test
  void builtInTemplatesAreReadableFromClasspath() {
    // 12 个内置模板类型必须全部能读到，缺一个都会让生成出的骨架对不上彼此的 import
    for (String code :
        new String[] {
          "entity",
          "repository",
          "createCommand",
          "updateCommand",
          "queryDto",
          "applicationService",
          "createRequest",
          "updateRequest",
          "pageQuery",
          "response",
          "assembler",
          "controller"
        }) {
      String content = gateway.contentOf(code).orElse(null);
      assertNotNull(content, "classpath 缺少内置模板: " + code);
      assertTrue(content.contains("package ${utils.getPackagePath"), code + " 模板内容异常");
    }
    assertTrue(gateway.contentOf("notExists").isEmpty());
  }

  @Test
  void templateWithoutContentIsStillValidWhenBuiltInExists() {
    doReturn(CodeTemplate.builder().code("entity").content(null).build())
        .when(repository)
        .findByIdAllTenants(1L);

    assertTrue(validate.handle("1"), "内置模板不应因 content 为空被判无效");
    assertNotNull(preview.handle("1", Map.of()));
  }

  @Test
  void customContentOverridesBuiltIn() {
    doReturn(CodeTemplate.builder().code("entity").content("自定义正文").build())
        .when(repository)
        .findByIdAllTenants(2L);

    assertTrue(validate.handle("2"));
    assertEquals("自定义正文", preview.handle("2", Map.of()));
  }

  @Test
  void unknownTemplateIdFailsFast() {
    doReturn((CodeTemplate) null).when(repository).findByIdAllTenants(3L);

    assertThrows(NotFoundException.class, () -> validate.handle("3"));
    assertThrows(NotFoundException.class, () -> preview.handle("3", Map.of()));
  }

  @Test
  void detailQueryBackfillsBuiltInContentForPreview() {
    // 前端「模板预览」直接展示详情接口返回的 content；内置模板行在库里 content 为空，
    // 不回填的话用户点开内置模板看到的就是空白（0009 把内置行正文清空后的回归点）。
    CodeTemplateRepository templateRepository = mock(CodeTemplateRepository.class);
    GetCodeTemplateDetailQueryApplicationService detail =
        new GetCodeTemplateDetailQueryApplicationService(templateRepository, gateway);

    doReturn(CodeTemplate.builder().code("entity").content(null).status("PUBLISHED").build())
        .when(templateRepository)
        .findByIdAllTenants(11L);
    CodeTemplate builtIn = detail.handle(GetCodeTemplateDetailQuery.builder().id(11L).build());
    assertNotNull(builtIn.getContent());
    assertTrue(builtIn.getContent().contains("TenantAggregateRoot"));
    // 回填只是读侧投影，不得把已发布状态改成 DRAFT
    assertEquals("PUBLISHED", builtIn.getStatus());

    doReturn(CodeTemplate.builder().code("entity").content("自定义").status("PUBLISHED").build())
        .when(templateRepository)
        .findByIdAllTenants(12L);
    assertEquals(
        "自定义", detail.handle(GetCodeTemplateDetailQuery.builder().id(12L).build()).getContent());
  }

  @Test
  void unknownTemplateCodeHasNoContent() {
    doReturn(CodeTemplate.builder().code("unknownType").content(null).build())
        .when(repository)
        .findByIdAllTenants(4L);

    assertEquals(false, validate.handle("4"));
    assertNull(preview.handle("4", Map.of()));
  }
}
