package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单分页查询入参（adapter 层 HTTP 入参，对齐《Bone-DDD》E-13.1）。
 *
 * <p><b>为什么叫 Request 而不是 Qry</b>：本工程adapter 层分页入参统一以 {@code Request} 收尾（{@code CreateOrderReq}等）， 而
 * {@code Qry} 后缀在全工程仅此一处，属于孤例；且 {@code Qry} 与 application 层的 {@code OrderPageQuery} 只差一个字母，
 * 靠命名区分层次反而增加阅读负担。层次差异由包名（{@code adapter.web.dto.request} vs {@code application.query}）表达更准确。
 *
 * <p><b>生成器约定</b>：studio-generator 的 {@code PageQueryGenerator} 以 {@code <Entity>PageQry} 为文件名模板，
 * 本类是对齐基准；改名时生成器模板与 {@code GeneratedLayoutTest} 已同步为 {@code PageRequest}。
 */
@Data
public class OrderPageRequest {

  private Long customerId;

  private String status;

  @Min(value = 1, message = "页码必须大于等于1")
  private Integer page = 1;

  @Min(value = 1, message = "每页大小必须大于等于1")
  @Max(value = 100, message = "每页大小不能超过100")
  private Integer size = 10;
}
