package com.bone.system.application.query.dto;

import com.bone.system.domain.model.dict.SysDictType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字典类型的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictTypeDto {
  private Long id;
  private Long tenantId;
  private String code;
  private String name;
  private String category;
  private String moduleCode;
  private String enumClass;
  private Integer maxDepth;
  private String description;
  private Integer builtin;
  private Integer editable;
  private Integer sort;
  private Integer status;
  private Long itemCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static DictTypeDto from(SysDictType type) {
    return DictTypeDto.builder()
        .id(type.getId())
        .tenantId(type.getTenantId())
        .code(type.getCode())
        .name(type.getName())
        .category(type.getCategory())
        .moduleCode(type.getModuleCode())
        .enumClass(type.getEnumClass())
        .maxDepth(type.getMaxDepth())
        .description(type.getDescription())
        .builtin(type.getBuiltin())
        .editable(type.getEditable())
        .sort(type.getSort())
        .status(type.getStatus())
        .createdAt(type.getCreatedAt())
        .updatedAt(type.getUpdatedAt())
        .build();
  }

  /** 是否平台级（租户不可改定义，只能覆盖项）。 */
  public boolean isPlatformOwned() {
    return tenantId == null || tenantId == 0L;
  }
}
