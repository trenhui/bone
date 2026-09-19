package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单分页查询入参（adapter 层 HTTP 入参，对齐《Bone-DDD》E-13.1）。
 *
 * <p>与 application 层 {@code OrderPageQuery} 区分：本类仅承载 HTTP 查询参数与校验。
 */
@Data
public class OrderPageQry {

  private Long customerId;

  private String status;

  @Min(value = 1, message = "页码必须大于等于1")
  private Integer pageNum = 1;

  @Min(value = 1, message = "每页大小必须大于等于1")
  @Max(value = 100, message = "每页大小不能超过100")
  private Integer pageSize = 10;
}
