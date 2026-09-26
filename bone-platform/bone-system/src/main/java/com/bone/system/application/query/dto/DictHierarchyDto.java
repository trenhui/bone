package com.bone.system.application.query.dto;

import com.bone.system.domain.model.dict.SysDictHierarchy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 层级关系投影：导出/导入时承载「哪套视图、谁挂谁」。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictHierarchyDto {
  private String typeCode;
  private String hierarchyCode;
  private String code;
  private String parentCode;
  private Integer sort;

  public static DictHierarchyDto from(SysDictHierarchy node) {
    return DictHierarchyDto.builder()
        .typeCode(node.getTypeCode())
        .hierarchyCode(node.getHierarchyCode())
        .code(node.getCode())
        .parentCode(node.getParentCode())
        .sort(node.getSort())
        .build();
  }
}
