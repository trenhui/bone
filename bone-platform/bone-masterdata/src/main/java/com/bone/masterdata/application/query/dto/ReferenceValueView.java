package com.bone.masterdata.application.query.dto;

import com.bone.masterdata.domain.model.reference.ReferenceValue;
import com.bone.masterdata.domain.model.reference.TenantReferenceValue;
import lombok.Builder;
import lombok.Getter;

/**
 * 参考数据值合并视图（G15，2026-09-26 overlay 拆分）：按值域合并「平台值 + 当前租户私有值」后的只读视图， {@code scope}
 * 标识来源层，供前端区分平台标准值与租户扩展值。
 */
@Getter
@Builder
public class ReferenceValueView {

  /** 来源层：PLATFORM（平台标准值）/ TENANT（当前租户私有扩展值）。 */
  public static final String SCOPE_PLATFORM = "PLATFORM";

  public static final String SCOPE_TENANT = "TENANT";

  private Long id;
  private Long setId;
  private String valueCode;
  private String valueName;
  private String externalCode;
  private Integer sortOrder;
  private Boolean enabled;
  private String scope;

  public static ReferenceValueView fromPlatform(ReferenceValue value) {
    return ReferenceValueView.builder()
        .id(value.getId())
        .setId(value.getSetId())
        .valueCode(value.getValueCode())
        .valueName(value.getValueName())
        .externalCode(value.getExternalCode())
        .sortOrder(value.getSortOrder())
        .enabled(value.getEnabled())
        .scope(SCOPE_PLATFORM)
        .build();
  }

  public static ReferenceValueView fromTenant(TenantReferenceValue value) {
    return ReferenceValueView.builder()
        .id(value.getId())
        .setId(value.getSetId())
        .valueCode(value.getValueCode())
        .valueName(value.getValueName())
        .externalCode(value.getExternalCode())
        .sortOrder(value.getSortOrder())
        .enabled(value.getEnabled())
        .scope(SCOPE_TENANT)
        .build();
  }
}
