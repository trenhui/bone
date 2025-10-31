package com.bone.metadata.sdk.metadata.client;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.support.config.MetadataServiceClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "metadata-service",
        url = "${metadata.service.config.remote.endpoint:http://localhost:9001}",
        configuration = MetadataServiceClientConfig.class
)
public interface MetadataServiceClient {

    @PostMapping("/v1/metadata/fields:search")
    List<FieldMetadata> findExtensionFields(@RequestBody AllocationContext context);

    @PostMapping("/v1/metadata/fields:searchByNames")
    List<FieldMetadata> findExtensionFieldsByNames(@RequestBody FieldsByNamesRequest request);

    @PostMapping("/v1/metadata/fields:allocate")
    List<FieldMetadata> allocateAndPersistFields(@RequestBody List<FieldMetadata> fields);

    @GetMapping("/v1/metadata/health")
    ResponseEntity<Void> healthCheck();
}