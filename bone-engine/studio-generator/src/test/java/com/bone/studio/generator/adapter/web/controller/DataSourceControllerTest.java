package com.bone.studio.generator.adapter.web.controller;

import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.DeleteDataSourceCommand;
import com.bone.studio.generator.application.command.cmd.TestDataSourceConnectionCommand;
import com.bone.studio.generator.application.command.cmd.UpdateDataSourceCommand;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQry;
import com.bone.studio.generator.application.usecase.GetDataSourceListUseCase;
import com.bone.studio.generator.application.usecase.TestDataSourceConnectionUseCase;
import com.bone.studio.generator.application.usecase.standard.CreateDataSourceUseCase;
import com.bone.studio.generator.application.usecase.standard.DeleteDataSourceUseCase;
import com.bone.studio.generator.application.usecase.standard.UpdateDataSourceUseCase;
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
    private CreateDataSourceUseCase createDataSourceUseCase;

    @Autowired
    private UpdateDataSourceUseCase updateDataSourceUseCase;

    @Autowired
    private DeleteDataSourceUseCase deleteDataSourceUseCase;

    @Autowired
    private TestDataSourceConnectionUseCase testDataSourceConnectionUseCase;

    @Autowired
    private GetDataSourceListUseCase getDataSourceListUseCase;

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
        GetDataSourceListQry query = GetDataSourceListQry.builder()
                .page(1)
                .size(10)
                .build();

        PageResult<DataSource> result = getDataSourceListUseCase.execute(query);

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

        String dataSourceId = createDataSourceUseCase.execute(createCommand);
        assertNotNull(dataSourceId);
        assertFalse(dataSourceId.isEmpty());

        GetDataSourceListQry query = GetDataSourceListQry.builder()
                .page(1)
                .size(100)
                .build();

        PageResult<DataSource> result = getDataSourceListUseCase.execute(query);
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

        String dataSourceId = createDataSourceUseCase.execute(createCommand);

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

        String result = updateDataSourceUseCase.execute(updateCommand);
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

        String dataSourceId = createDataSourceUseCase.execute(createCommand);

        GetDataSourceListQry queryBeforeDelete = GetDataSourceListQry.builder()
                .page(1)
                .size(100)
                .build();
        PageResult<DataSource> resultBeforeDelete = getDataSourceListUseCase.execute(queryBeforeDelete);
        long countBefore = resultBeforeDelete.getRecords().size();

        DeleteDataSourceCommand deleteCommand = DeleteDataSourceCommand.builder()
                .id(dataSourceId)
                .build();
        deleteDataSourceUseCase.execute(deleteCommand);

        GetDataSourceListQry queryAfterDelete = GetDataSourceListQry.builder()
                .page(1)
                .size(100)
                .build();
        PageResult<DataSource> resultAfterDelete = getDataSourceListUseCase.execute(queryAfterDelete);
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

        String dataSourceId = createDataSourceUseCase.execute(createCommand);

        TestDataSourceConnectionCommand command = TestDataSourceConnectionCommand.builder()
                .id(dataSourceId)
                .build();

        boolean result = testDataSourceConnectionUseCase.execute(command);
        assertTrue(result);
    }
}