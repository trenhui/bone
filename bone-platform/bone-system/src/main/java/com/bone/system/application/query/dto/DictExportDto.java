package com.bone.system.application.query.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 值域快照（导出 / 导入同构，upsert 语义）。
 *
 * <p>包含四类数据：类型定义、扁平的项、层级关系、译文。四者分开是因为它们生命周期不同—— 只搬项而不搬层级，导入后会得到一堆散在根上的值；只搬层级不搬项，则是一堆指向空的关系。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictExportDto {
  private DictTypeDto type;
  @Builder.Default private List<DictItemDto> items = List.of();
  @Builder.Default private List<DictHierarchyDto> hierarchies = List.of();
  @Builder.Default private List<DictItemTextDto> texts = List.of();
}
