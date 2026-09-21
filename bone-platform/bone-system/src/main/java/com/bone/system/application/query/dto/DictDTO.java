package com.bone.system.application.query.dto;

import com.bone.system.domain.dict.SysDict;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字典项的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictDto {
  private Long id;
  private String type;
  private String typeName;
  private String code;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 聚合 → 应用投影。 */
  public static DictDto from(SysDict dict) {
    return DictDto.builder()
        .id(dict.getId())
        .type(dict.getType().value())
        .typeName(dict.getTypeName())
        .code(dict.getCode())
        .label(dict.getLabel())
        .value(dict.getValue())
        .sort(dict.getSort())
        .status(dict.getStatus())
        .createdAt(dict.getCreatedAt())
        .updatedAt(dict.getUpdatedAt())
        .build();
  }
}
