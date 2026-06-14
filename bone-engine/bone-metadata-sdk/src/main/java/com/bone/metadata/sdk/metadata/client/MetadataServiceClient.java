package com.bone.metadata.sdk.metadata.client;

import com.bone.core.web.PlatformApiPaths;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.support.config.MetadataServiceClientConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "metadata-service",
    url = "${metadata.service.config.remote.endpoint:http://localhost:9001}",
    configuration = MetadataServiceClientConfig.class)
public interface MetadataServiceClient {

  @PostMapping(PlatformApiPaths.METADATA_V1 + "/fields:search")
  List<FieldMetadata> findExtensionFields(@RequestBody AllocationContext context);

  @PostMapping(PlatformApiPaths.METADATA_V1 + "/fields:searchByNames")
  List<FieldMetadata> findExtensionFieldsByNames(@RequestBody FieldsByNamesRequest request);

  @PostMapping(PlatformApiPaths.METADATA_V1 + "/fields:allocate")
  List<FieldMetadata> allocateAndPersistFields(@RequestBody List<FieldMetadata> fields);

  @GetMapping(PlatformApiPaths.METADATA_V1 + "/health")
  ResponseEntity<Void> healthCheck();
}
