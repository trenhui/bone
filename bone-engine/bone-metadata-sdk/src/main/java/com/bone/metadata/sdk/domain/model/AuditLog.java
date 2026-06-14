package com.bone.metadata.sdk.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
}
