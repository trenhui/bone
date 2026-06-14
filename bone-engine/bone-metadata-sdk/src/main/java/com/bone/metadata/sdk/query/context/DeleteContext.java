package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.Getter;

@Getter
public class DeleteContext {
  private final TableMetadata table;
  private final Criteria<?> criteria;
  private final AllocationContext extContext;

  public DeleteContext(TableMetadata table, Criteria<?> criteria, AllocationContext extContext) {
    this.table = table;
    this.criteria = criteria;
    this.extContext = extContext;
  }
}
