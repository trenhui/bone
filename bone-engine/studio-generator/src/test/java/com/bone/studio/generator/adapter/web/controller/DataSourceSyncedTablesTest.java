package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DataSourceSyncedTablesTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void syncedTablesEndpointReturnsOkForUnknownDataSource() {
    String url = "http://localhost:" + port + "/api/v1/generator/data-sources/999999/synced-tables";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }
}
