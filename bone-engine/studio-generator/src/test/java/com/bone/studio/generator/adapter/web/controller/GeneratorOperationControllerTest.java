package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeneratorOperationControllerTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void getOperation_notFound() {
    String url = "http://localhost:" + port + "/api/v1/generator/operations/op-missing";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void getTaskStatus_unknownTask() {
    String url =
        "http://localhost:" + port + "/api/v1/generator/code-generation/tasks/unknown/status";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() != null && response.getBody().contains("UNKNOWN"));
  }
}
