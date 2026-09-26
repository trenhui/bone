package com.bone.system.application.query.dto;

import com.bone.system.domain.model.dict.SysDictItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典项的应用投影。
 *
 * <p>{@code parentCode} / {@code level} / {@code path} / {@code hierarchyCode} 来自层级关系表，
 * 是<b>视图字段</b>而非项本身的属性——同一个项在不同层级视图里父级不同，所以它们只能随视图一起投影， 不能落在项上（这正是把层级拆出去的意义）。
 *
 * <p>{@code children} 只在树形接口填充，分页与下拉恒为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictItemDto {
  private Long id;
  private Long tenantId;
  private String typeCode;
  private String code;
  private String label;
  private String value;
  private String enumName;
  private String tagType;
  private String i18nKey;
  private String externalCode;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  private Integer isDefault;
  private Integer sort;
  private Integer status;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // ---- 层级视图字段 ----
  private String hierarchyCode;
  private String parentCode;
  private Integer level;
  private String path;
  private boolean hasChildren;
  @Builder.Default private List<DictItemDto> children = new ArrayList<>();

  public static DictItemDto from(SysDictItem item) {
    return DictItemDto.builder()
        .id(item.getId())
        .tenantId(item.getTenantId())
        .typeCode(item.getTypeCode())
        .code(item.getCode())
        .label(item.getLabel())
        .value(item.getValue())
        .enumName(item.getEnumName())
        .tagType(item.getTagType())
        .i18nKey(item.getI18nKey())
        .externalCode(item.getExternalCode())
        .effectiveFrom(item.getEffectiveFrom())
        .effectiveTo(item.getEffectiveTo())
        .isDefault(item.getIsDefault())
        .sort(item.getSort())
        .status(item.getStatus())
        .description(item.getDescription())
        .createdAt(item.getCreatedAt())
        .updatedAt(item.getUpdatedAt())
        .build();
  }

  public boolean isEnabled() {
    return status != null && status == 1;
  }

  public boolean isDefaultItem() {
    return isDefault != null && isDefault == 1;
  }

  /** 当前时刻是否落在生效区间（Oracle 时间有效性口径）。 */
  public boolean isEffectiveNow() {
    LocalDateTime now = LocalDateTime.now();
    return (effectiveFrom == null || !now.isBefore(effectiveFrom))
        && (effectiveTo == null || !now.isAfter(effectiveTo));
  }
}
