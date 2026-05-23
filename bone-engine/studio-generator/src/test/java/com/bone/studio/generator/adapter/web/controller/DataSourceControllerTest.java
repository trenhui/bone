package com.bone.studio.generator.adapter.web.controller;

import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.DeleteDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.TestDataSourceConnectionCommand;
import com.bone.studio.generator.application.command.cmd.UpdateDataSourceCommand;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQuery;
import com.bone.studio.generator.application.query.handler.GetDataSourceListQueryHandler;
import com.bone.studio.generator.application.command.handler.TestDataSourceConnectionHandler;
import com.bone.studio.generator.application.command.handler.CreateDataSourceHandler;
import com.bone.studio.generator.application.command.handler.DeleteDataSourceHandler;
import com.bone.studio.generator.application.command.handler.UpdateDataSourceHandler;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.core.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DataSourceControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CreateDataSourceHandler createDataSourceHandler;

    @Autowired
    private UpdateDataSourceHandler updateDataSourceHandler;

    @Autowired
    private DeleteDataSourceHandler deleteDataSourceHandler;

    @Autowired
    private TestDataSourceConnectionHandler testDataSourceConnectionHandler;

    @Autowired
    private GetDataSourceListQueryHandler queryHandler;

    private String baseUrl;

    private String createdDataSourceId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/generator/data-sources";
    }

    @Test
    void testCreateDataSource() {
        CreateDataSourceCommand command = CreateDataSourceCommand.builder()
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
        GetDataSourceListQuery query = GetDataSourceListQuery.builder()
                .page(1)
                .size(10)
                .build();

        PageResult<DataSource> result = queryHandler.handle(query);

        assertNotNull(result);
        assertNotNull(result.getRecords());
    }

    @Test
    void testCreateAndGetDataSource() {
        CreateDataSourceCommand createCommand = CreateDataSourceCommand.builder()
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

        GetDataSourceListQuery query = GetDataSourceListQuery.builder()
                .page(1)
                .size(100)
                .build();

        PageResult<DataSource> result = queryHandler.handle(query);
        assertNotNull(result);
        assertNotNull(result.getRecords());

        boolean found = result.getRecords().stream()
                .anyMatch(ds -> String.valueOf(ds.getId()).equals(dataSourceId));
        assertTrue(found, "创建的数据源应该能在列表中找到");
    }

    @Test
    void testUpdateDataSource() {
        CreateDataSourceCommand createCommand = CreateDataSourceCommand.builder()
                .name("待更新数据源")
                .type("mysql")
                .host("localhost")
                .port("3306")
                .database("test")
                .username("root")
                .password("123456")
                .build();

        String dataSourceId = createDataSourceHandler.handle(createCommand);

        UpdateDataSourceCommand updateCommand = UpdateDataSourceCommand.builder()
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
        CreateDataSourceCommand createCommand = CreateDataSourceCommand.builder()
                .name("待删除数据源")
                .type("mysql")
                .host("localhost")
                .port("3306")
                .database("test")
                .username("root")
                .password("123456")
                .build();

        String dataSourceId = createDataSourceHandler.handle(createCommand);

        GetDataSourceListQuery queryBeforeDelete = GetDataSourceListQuery.builder()
                .page(1)
                .size(100)
                .build();
        PageResult<DataSource> resultBeforeDelete = queryHandler.handle(queryBeforeDelete);
        long countBefore = resultBeforeDelete.getRecords().size();

        DeleteDataSourceCommand deleteCommand = DeleteDataSourceCommand.builder()
                .id(dataSourceId)
                .build();
        deleteDataSourceHandler.handle(deleteCommand);

        GetDataSourceListQuery queryAfterDelete = GetDataSourceListQuery.builder()
                .page(1)
                .size(100)
                .build();
        PageResult<DataSource> resultAfterDelete = queryHandler.handle(queryAfterDelete);
        long countAfter = resultAfterDelete.getRecords().size();

        assertEquals(countBefore - 1, countAfter);
    }

    @Test
    void testTestDataSourceConnection() {
        CreateDataSourceCommand createCommand = CreateDataSourceCommand.builder()
                .name("连接测试数据源")
                .type("mysql")
                .host("localhost")
                .port("3306")
                .database("bone")
                .username("root")
                .password("mysql123")
                .build();

        String dataSourceId = createDataSourceHandler.handle(createCommand);

        TestDataSourceConnectionCommand command = TestDataSourceConnectionCommand.builder()
                .id(dataSourceId)
                .build();

        boolean result = testDataSourceConnectionHandler.handle(command);
        assertTrue(result);
    }
}