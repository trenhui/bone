package com.bone.metadata.sdk.metadata.client;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class FieldsByNamesRequest {
    private AllocationContext context;
    private List<String> logicalNames;
}
