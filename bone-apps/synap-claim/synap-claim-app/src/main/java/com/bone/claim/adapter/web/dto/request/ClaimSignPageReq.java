package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 签收批次分页查询（服务端真分页，条件对齐普康 TPA 签收台）。 */
public record ClaimSignPageReq(
    @Schema(description = "来源 ALL/ONLINE/OFFLINE，空同 ALL") String source,
    @Schema(description = "批次号（模糊）") @Size(max = 64) String batchNo,
    @Schema(description = "签收方式 AUTO/MANUAL") String signType,
    @Schema(description = "签收渠道（模糊）") @Size(max = 32) String signChannel,
    @Schema(description = "签收状态") String signStatus,
    @Schema(description = "保险公司（模糊）") @Size(max = 128) String insuranceCompany,
    @Schema(description = "保险分公司（模糊）") @Size(max = 128) String insuranceSubsidiary,
    @Schema(description = "投保公司（模糊）") @Size(max = 128) String insuringAgency,
    @Schema(description = "签收人员（模糊）") @Size(max = 64) String signOperator,
    @Schema(description = "签收时间起（ISO-8601）") Instant signTimeStart,
    @Schema(description = "签收时间止（ISO-8601）") Instant signTimeEnd,
    @Schema(description = "创建时间起（ISO-8601）") Instant createTimeStart,
    @Schema(description = "创建时间止（ISO-8601）") Instant createTimeEnd,
    @Schema(description = "页码（缺省1）", defaultValue = "1") @Min(1) Integer page,
    @Schema(description = "每页条数（缺省20）", defaultValue = "20") @Min(1) @Max(100) Integer size) {

  public int pageOrDefault() {
    return page == null ? 1 : page;
  }

  public int sizeOrDefault() {
    return size == null ? 20 : size;
  }

  @AssertTrue(message = "签收时间窗起止顺序非法")
  public boolean isSignTimeRangeValid() {
    return signTimeStart == null || signTimeEnd == null || !signTimeStart.isAfter(signTimeEnd);
  }

  @AssertTrue(message = "创建时间窗起止顺序非法")
  public boolean isCreateTimeRangeValid() {
    return createTimeStart == null
        || createTimeEnd == null
        || !createTimeStart.isAfter(createTimeEnd);
  }
}
