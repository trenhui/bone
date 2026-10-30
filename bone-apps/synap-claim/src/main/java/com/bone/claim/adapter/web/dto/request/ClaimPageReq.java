package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 待办/列表分页查询（工作台）。
 *
 * <p><b>page/size 必须可空 + 显式兜底</b>：2026-10-30 全模块联调实测，{@code GET /api/v1/claims} 不带分页参数时返回
 * 400「Failed to convert value of type 'null' to required type 'int'」—— 记录组件声明成<b>原始类型 {@code
 * int}</b>，而 {@code @Schema(defaultValue=...)} <b>只是 OpenAPI
 * 文档注解，不参与绑定</b>，于是「文档宣称有默认值、代码实际没有」：直接按文档调用（curl / OpenAPI 生成的客户端 / 网关重试）一律 400。改成包装类型 +
 * 显式兜底方法，让文档与行为一致。
 */
@Schema(description = "赔案分页查询")
public record ClaimPageReq(
    @Schema(description = "状态集（空 = 待核赔 40）") List<ClaimStatus> statuses,
    @Schema(description = "来源渠道（可空）") String sourceChannel,
    @Schema(description = "页码（缺省 1）", defaultValue = "1") @Min(value = 1, message = "页码从 1 开始")
        Integer page,
    @Schema(description = "每页条数（缺省 20，上限 100）", defaultValue = "20")
        @Min(value = 1, message = "每页至少 1 条")
        @Max(value = 100, message = "每页最多 100 条")
        Integer size) {

  /** 缺省页码（不传 page 时用）。 */
  public static final int DEFAULT_PAGE = 1;

  /** 缺省每页条数（不传 size 时用）。 */
  public static final int DEFAULT_SIZE = 20;

  /** 页码兜底值：null → {@value #DEFAULT_PAGE}。 */
  public int pageOrDefault() {
    return page == null ? DEFAULT_PAGE : page;
  }

  /** 每页条数兜底值：null → {@value #DEFAULT_SIZE}。 */
  public int sizeOrDefault() {
    return size == null ? DEFAULT_SIZE : size;
  }
}
