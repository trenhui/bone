package com.bone.claim.adapter.web.dto.request;

import com.bone.core.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保单分页查询入参。
 *
 * <p><b>为什么继承 {@link PageParam}</b>：Bone-API 规范 §5.1 要求对外分页入参继承框架基类， 由基类统一承担 {@code @Min(1)} /
 * {@code @Max(100)} 与 {@code size} 上限收窄。裸 {@code @RequestParam int page, int size} 不会触发 bean
 * validation，{@code size=Integer.MAX_VALUE} 会直接打满数据库；继承后 {@code @Valid @ModelAttribute} 才真正生效。
 *
 * <p>缺省每页 20 条沿用迁移前 Controller 的 {@code defaultValue}，避免改真源时顺手改了行为。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "保单分页查询")
public class PolicyPageQry extends PageParam {

  @Schema(description = "关键词：保单号 / 保司保单号 / 名称")
  private String keyword;

  @Schema(description = "规则开关过滤（可空）")
  private String configStatus;

  public PolicyPageQry() {
    setSize(20);
  }
}
