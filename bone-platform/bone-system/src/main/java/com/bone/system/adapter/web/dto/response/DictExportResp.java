package com.bone.system.adapter.web.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 值域快照（导出结果 / 导入载荷同构）：类型定义 + 项 + 层级关系 + 译文。 */
@Data
@Builder
public class DictExportResp {
  private DictTypeResp type;
  @Builder.Default private List<DictItemResp> items = List.of();
  @Builder.Default private List<DictHierarchyResp> hierarchies = List.of();
  @Builder.Default private List<DictItemTextResp> texts = List.of();
}
