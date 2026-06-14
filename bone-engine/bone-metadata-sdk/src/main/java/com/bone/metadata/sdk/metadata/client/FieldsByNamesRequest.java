package com.bone.metadata.sdk.metadata.client;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldsByNamesRequest {
  private AllocationContext context;
  private List<String> logicalNames;
}
