package com.bone.metadata.sdk.domain.model;

import java.time.Instant;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {
    private String traceId;
    private String principal;
    private String operation;
    private String resource;
    private String action;
    private String status;
    private String errorMessage;
    private Instant timestamp;
    
    public String getTraceId() {
        return traceId;
    }
    
    public String getPrincipal() {
        return principal;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getResource() {
        return resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
}