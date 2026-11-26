package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 签收批次分页查询（服务端真分页）。 */
public record ClaimSignPageReq(
    @Schema(description = "来源 ONLINE/OFFLINE") String source,
    @Schema(description = "签收状态") String signStatus,
    @Schema(description = "页码（缺省1）", defaultValue = "1") @Min(1) Integer page,
    @Schema(description = "每页条数（缺省20）", defaultValue = "20") @Min(1) @Max(100) Integer size) {

  public int pageOrDefault() {
    return page == null ? 1 : page;
  }

  public int sizeOrDefault() {
    return size == null ? 20 : size;
  }
}
