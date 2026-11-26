package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.adapter.web.ReviewTaskStatusMapper;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.core.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 案件灵活查询（GET /api/v1/claims/query）。 */
@Schema(description = "案件灵活查询条件")
public record ClaimQueryReq(
    @Schema(description = "跨键关键字：案件ID/赔案号/保单号/发票号/批次号/被保人ID")
        @Size(min = 2, max = 64, message = "关键字长度需 2..64 字符")
        String keyword,
    @Schema(description = "案件主键（精确）") @Size(max = 32) String claimId,
    @Schema(description = "赔案号（模糊）") @Size(max = 64) String claimNo,
    @Schema(description = "报案号（精确）") @Size(max = 64) String reportNo,
    @Schema(description = "保单号（模糊）") @Size(max = 64) String policyNo,
    @Schema(description = "案件状态集；与 taskStatus 二选一，taskStatus 优先") List<ClaimStatus> statuses,
    @Schema(description = "核赔任务维（WAITING/ASSIGNED/PROCESSING/DONE）")
        ReviewTaskStatusMapper.TaskStatus taskStatus,
    @Schema(description = "案件阶段") @Size(max = 32) String stage,
    @Schema(description = "来源渠道") @Size(max = 32) String sourceChannel,
    @Schema(description = "来源系统") @Size(max = 32) String sourceSystem,
    @Schema(description = "当前处理/初审人") @Size(max = 64) String assigneeId,
    @Schema(description = "挂起状态：0 正常 / 1 挂起") Integer hangUpStatus,
    @Schema(description = "受理时间起（YYYY-MM-DD，Asia/Shanghai）")
        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式需 YYYY-MM-DD")
        String createdFrom,
    @Schema(description = "受理时间止（YYYY-MM-DD，Asia/Shanghai，含当日）")
        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式需 YYYY-MM-DD")
        String createdTo,
    @Schema(description = "申请金额下限") BigDecimal amountMin,
    @Schema(description = "申请金额上限") BigDecimal amountMax,
    @Schema(description = "页码（缺省 1）", defaultValue = "1") @Min(value = 1, message = "页码从 1 开始")
        Integer page,
    @Schema(description = "每页条数（缺省 20，上限 100）", defaultValue = "20")
        @Min(value = 1, message = "每页至少 1 条")
        @Max(value = PageParam.MAX_PAGE_SIZE, message = "每页最多 100 条")
        Integer size) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_SIZE = 20;

  public int pageOrDefault() {
    return page == null ? DEFAULT_PAGE : page;
  }

  public int sizeOrDefault() {
    return size == null ? DEFAULT_SIZE : size;
  }

  public LocalDate fromDate() {
    return createdFrom == null || createdFrom.isBlank() ? null : LocalDate.parse(createdFrom);
  }

  public LocalDate toDate() {
    return createdTo == null || createdTo.isBlank() ? null : LocalDate.parse(createdTo);
  }

  @AssertTrue(message = "受理时间起始不能晚于结束")
  public boolean isCreatedRangeValid() {
    try {
      LocalDate from = fromDate();
      LocalDate to = toDate();
      return from == null || to == null || !from.isAfter(to);
    } catch (RuntimeException invalidDate) {
      // @Pattern 会单独报格式错误；此处不能再抛异常，否则 400 变成 500。
      return false;
    }
  }

  @AssertTrue(message = "申请金额下限不能大于上限")
  public boolean isAmountRangeValid() {
    return amountMin == null || amountMax == null || amountMin.compareTo(amountMax) <= 0;
  }
}
