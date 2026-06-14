package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateDescriptor {
  private String templateId; // Unique identifier (e.g., com.example.BarRepo.findById)
  private String sourceUri; // Source URI (e.g., annotation://, classpath://)
  private SqlTemplateType format; // Template format (SQL, MYBATIS, DYNAMIC_SQL)

  @Builder.Default
  private Map<String, String> tags =
      Collections.emptyMap(); // Metadata tags (e.g., env=prod, tenant=acme)

  private String version; // Template version (e.g., v1.0)
  private String checksum; // Content checksum for validation
  private Duration ttl; // Time-to-live for caching
  private String fallbackId; // Fallback template ID for resilience
}
