package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.studio.generator.application.CreateDataSourceApplicationService;
import com.bone.studio.generator.application.DeleteDataSourceApplicationService;
import com.bone.studio.generator.application.GetDataSourceListQueryApplicationService;
import com.bone.studio.generator.application.TestDataSourceConnectionApplicationService;
import com.bone.studio.generator.application.UpdateDataSourceApplicationService;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.DeleteDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.TestDataSourceConnectionCommand;
import com.bone.studio.generator.application.command.cmd.UpdateDataSourceCommand;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQuery;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.support.TestTenantContextConfiguration;
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
class DataSourceControllerTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private CreateDataSourceApplicationService createDataSourceHandler;

  @Autowired private UpdateDataSourceApplicationService updateDataSourceHandler;

  @Autowired private DeleteDataSourceApplicationService deleteDataSourceHandler;

  @Autowired private TestDataSourceConnectionApplicationService testDataSourceConnectionHandler;

  @Autowired private GetDataSourceListQueryApplicationService queryHandler;

  private String baseUrl;

  private String createdDataSourceId;

  @BeforeEach
  void setUp() {
    // 测试线程直调 Handler / QueryHandler 需自备租户上下文（HTTP 请求由测试过滤器注入）
    TenantContext.setTenantId(TestTenantContextConfiguration.TEST_TENANT_ID);
    baseUrl = "http://localhost:" + port + "/api/v1/generator/data-sources";
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void testCreateDataSource() {
    CreateDataSourceCommand command =
        CreateDataSourceCommand.builder()
            .name("测试数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("test")
            .username("root")
            .password("123456")
            .build();

    ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, command, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    createdDataSourceId = response.getBody().replace("\"", "");
  }

  @Test
  void testGetDataSourceList() {
    GetDataSourceListQuery query = GetDataSourceListQuery.builder().page(1).size(10).build();

    PageResult<DataSource> result = queryHandler.handle(query);

    assertNotNull(result);
    assertNotNull(result.getRecords());
  }

  @Test
  void testCreateAndGetDataSource() {
    CreateDataSourceCommand createCommand =
        CreateDataSourceCommand.builder()
            .name("集成测试数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("test")
            .username("root")
            .password("123456")
            .build();

    String dataSourceId = createDataSourceHandler.handle(createCommand);
    assertNotNull(dataSourceId);
    assertFalse(dataSourceId.isEmpty());

    GetDataSourceListQuery query = GetDataSourceListQuery.builder().page(1).size(100).build();

    PageResult<DataSource> result = queryHandler.handle(query);
    assertNotNull(result);
    assertNotNull(result.getRecords());

    boolean found =
        result.getRecords().stream()
            .anyMatch(ds -> String.valueOf(ds.getId()).equals(dataSourceId));
    assertTrue(found, "创建的数据源应该能在列表中找到");
  }

  @Test
  void testUpdateDataSource() {
    CreateDataSourceCommand createCommand =
        CreateDataSourceCommand.builder()
            .name("待更新数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("test")
            .username("root")
            .password("123456")
            .build();

    String dataSourceId = createDataSourceHandler.handle(createCommand);

    UpdateDataSourceCommand updateCommand =
        UpdateDataSourceCommand.builder()
            .id(dataSourceId)
            .name("更新后的数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("test")
            .username("root")
            .password("123456")
            .build();

    String result = updateDataSourceHandler.handle(updateCommand);
    assertNotNull(result);
  }

  @Test
  void testDeleteDataSource() {
    CreateDataSourceCommand createCommand =
        CreateDataSourceCommand.builder()
            .name("待删除数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("test")
            .username("root")
            .password("123456")
            .build();

    String dataSourceId = createDataSourceHandler.handle(createCommand);

    GetDataSourceListQuery queryBeforeDelete =
        GetDataSourceListQuery.builder().page(1).size(100).build();
    PageResult<DataSource> resultBeforeDelete = queryHandler.handle(queryBeforeDelete);
    long countBefore = resultBeforeDelete.getRecords().size();

    DeleteDataSourceCommand deleteCommand =
        DeleteDataSourceCommand.builder().id(dataSourceId).build();
    deleteDataSourceHandler.handle(deleteCommand);

    GetDataSourceListQuery queryAfterDelete =
        GetDataSourceListQuery.builder().page(1).size(100).build();
    PageResult<DataSource> resultAfterDelete = queryHandler.handle(queryAfterDelete);
    long countAfter = resultAfterDelete.getRecords().size();

    assertEquals(countBefore - 1, countAfter);
  }

  @Test
  void testTestDataSourceConnection() {
    CreateDataSourceCommand createCommand =
        CreateDataSourceCommand.builder()
            .name("连接测试数据源")
            .type("mysql")
            .host("localhost")
            .port("3306")
            .database("bone")
            .username("root")
            .password("mysql123")
            .build();

    String dataSourceId = createDataSourceHandler.handle(createCommand);

    TestDataSourceConnectionCommand command =
        TestDataSourceConnectionCommand.builder().id(dataSourceId).build();

    boolean result = testDataSourceConnectionHandler.handle(command);
    assertTrue(result);
  }
}
