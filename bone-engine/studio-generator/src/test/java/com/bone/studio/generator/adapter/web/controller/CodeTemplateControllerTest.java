package com.bone.studio.generator.adapter.web.controller;

import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQry;
import com.bone.studio.generator.application.usecase.CreateCodeTemplateUseCase;
import com.bone.studio.generator.application.usecase.GetCodeTemplateListUseCase;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.core.result.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeTemplateControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CreateCodeTemplateUseCase createCodeTemplateUseCase;

    @Autowired
    private GetCodeTemplateListUseCase getCodeTemplateListUseCase;

    private String baseUrl;

    @jakarta.annotation.PostConstruct
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/code-templates";
    }

    @Test
    void testCreateCodeTemplate() {
        String uniqueName = "测试模板_" + UUID.randomUUID().toString().substring(0, 8);
        CreateCodeTemplateCommand command = CreateCodeTemplateCommand.builder()
                .name(uniqueName)
                .code("test_template")
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
        GetCodeTemplateListQry query = GetCodeTemplateListQry.builder()
                .page(1)
                .size(10)
                .build();

        PageResult<CodeTemplate> result = getCodeTemplateListUseCase.execute(query);

        assertNotNull(result);
        assertNotNull(result.getList());
    }

    @Test
    void testGetCodeTemplateListContainsData() {
        GetCodeTemplateListQry query = GetCodeTemplateListQry.builder()
                .page(1)
                .size(100)
                .build();

        PageResult<CodeTemplate> result = getCodeTemplateListUseCase.execute(query);

        assertNotNull(result);
        assertNotNull(result.getList());
    }
}