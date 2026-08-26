package com.bone.metadata.engine.domain.metadata;

import lombok.Getter;
import lombok.Setter;

/** 索引字段模型类 */
@Getter
@Setter
public class IndexField {

  // 字段API名称
  private String fieldApiName;

  // 排序方向（ASC或DESC）
  private String direction = "ASC";

  // 索引类型（如TEXT, NUMBER等）
  private String indexType;
}
