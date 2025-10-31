package com.bone.metadata.sdk.metadata;


import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.metadata.client.FieldsByNamesRequest;
import com.bone.metadata.sdk.metadata.client.MetadataServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

@RefreshScope
@Slf4j
public class RemoteMetadataService implements MetadataService {
    private final MetadataServiceClient metadataServiceClient;

    public RemoteMetadataService(MetadataServiceClient metadataServiceClient) {
        this.metadataServiceClient = metadataServiceClient;
    }

    @Override
    public List<FieldMetadata> findExtensionFields(AllocationContext context) {
        return metadataServiceClient.findExtensionFields(context);
    }

    @Override
    public List<FieldMetadata> findExtensionFieldsByNames(AllocationContext context, List<String> logicalNames) {
        if (logicalNames == null || logicalNames.isEmpty()) {
            return Collections.emptyList();
        }
        return metadataServiceClient.findExtensionFieldsByNames(new FieldsByNamesRequest(context, logicalNames));
    }

    @Override
    public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
        try {
            if (fields == null || fields.isEmpty()) {
                return Collections.emptyList();
            }
            return metadataServiceClient.allocateAndPersistFields(fields);
        } catch (Exception ex) {
            log.error("allocateAndPersistFields error ", ex);
            throw new FieldAllocationException(ex.getMessage(), ex);
        }
    }

    /**
     * 健康检查方法
     *
     * @return 元数据服务是否健康
     */
    @Override
    public boolean isHealthy() {
        try {
            ResponseEntity<Void> response = metadataServiceClient.healthCheck();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Remote service health check failed", e);
            return false;
        }
    }
}