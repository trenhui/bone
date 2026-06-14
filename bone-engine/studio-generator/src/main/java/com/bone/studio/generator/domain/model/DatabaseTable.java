package com.bone.studio.generator.domain.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatabaseTable {
  private String id;
  private String tableName;
  private String tableComment;
  private List<TableColumn> columns;
}
