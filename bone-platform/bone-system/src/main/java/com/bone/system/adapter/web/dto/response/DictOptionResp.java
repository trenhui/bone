package com.bone.system.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

/** 下拉数据源响应（平台消费字典的统一形状）。 */
@Data
@Builder
public class DictOptionResp {
  private String code;
  private String label;
  private String value;
  private String tagType;
  private boolean isDefault;
  private Integer sort;

  /** 生命周期状态（v1.1 三态）：ACTIVE / DEPRECATED；DISABLED 不返回。渲染按 ACTIVE 过滤，写校验接受两种。 */
  private String state;

  /** 值域业务版本号（v1.1 版本协商 / SWR）：值域内容任一变更都递增，供消费方判定是否需要刷新。 */
  private Long version;
}
