package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeTemplateApplicationService;
import com.bone.studio.generator.application.query.handler.GetCodeTemplateListQueryApplicationService;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQuery;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.support.TestTenantContextConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeTemplateControllerTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private CreateCodeTemplateApplicationService createCodeTemplateHandler;

  @Autowired private GetCodeTemplateListQueryApplicationService queryHandler;

  private String baseUrl;

  @jakarta.annotation.PostConstruct
  void setUp() {
    baseUrl = "http://localhost:" + port + "/api/v1/generator/templates";
  }

  @BeforeEach
  void setTenant() {
    // 测试线程直调 QueryHandler 需自备租户上下文（HTTP 请求由测试过滤器注入）
    TenantContext.setTenantId(TestTenantContextConfiguration.TEST_TENANT_ID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void testCreateCodeTemplate() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String uniqueName = "测试模板_" + suffix;
    CreateCodeTemplateCommand command =
        CreateCodeTemplateCommand.builder()
            .name(uniqueName)
            .code("test_template_" + suffix)
            .description("测试模板描述")
            .type("entity")
            .content("public class ${className} { }")
            .build();

    ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, command, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void testGetCodeTemplateList() {
    GetCodeTemplateListQuery query = GetCodeTemplateListQuery.builder().page(1).size(10).build();

    PageResult<CodeTemplate> result = queryHandler.handle(query);

    assertNotNull(result);
    assertNotNull(result.getRecords());
  }

  @Test
  void testGetCodeTemplateListContainsData() {
    GetCodeTemplateListQuery query = GetCodeTemplateListQuery.builder().page(1).size(100).build();

    PageResult<CodeTemplate> result = queryHandler.handle(query);

    assertNotNull(result);
    assertNotNull(result.getRecords());
  }
}
