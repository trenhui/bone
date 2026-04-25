package com.bone.studio.generator.adapter.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TableMetadataControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @jakarta.annotation.PostConstruct
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/table-metadata";
    }

    @Test
    void testApiEndpointExists() {
        String url = baseUrl + "/sync";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertNotNull(response);
    }
}