package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeGenerationControllerTest {

    private static final String TASK_ID = "test-task-id-123";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GenerationTaskRepository generationTaskRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/generator/code-generation";
        seedSuccessTask();
    }

    @Test
    void testGetTaskStatus() {
        String url = baseUrl + "/tasks/" + TASK_ID + "/status";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("SUCCESS"));
    }

    private void seedSuccessTask() {
        try {
            GenerationTask existing = generationTaskRepository.findOneByCriteria(
                    Criteria.<GenerationTask>create().eq("taskId", TASK_ID));
            if (existing != null) {
                existing.markCompleted(null, "http://localhost/zip");
                generationTaskRepository.save(existing);
                return;
            }
        } catch (MultipleResultsException ex) {
            throw new IllegalStateException("duplicate generation task: " + TASK_ID, ex);
        }
        GenerationTask task = GenerationTask.create(
                DistributedIdGenerator.generateLongId(),
                0L,
                TASK_ID,
                "demo",
                "com.demo",
                "demo",
                null,
                null,
                null,
                "{}");
        task.markCompleted(null, "http://localhost/zip");
        generationTaskRepository.save(task);
    }
}
