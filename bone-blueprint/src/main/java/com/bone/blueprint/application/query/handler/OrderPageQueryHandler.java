package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.dto.OrderHeadRow;
import com.bone.blueprint.application.query.port.OrderReadPort;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.application.query.support.OrderSummaryAssembler;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单分页查询（读侧）。
 *
 * <p><b>读侧实现选择标准</b>（样板约定，E-4.2）：读侧端口固定在 {@code application/query/port}，实现固定在 {@code
 * infrastructure/query}（见 {@code infrastructure/query/OrderReadPortImpl}）；{@code QueryBuilder} /
 * {@code Criteria} / SQL 只许出现在 infrastructure，application 与 domain 都不得依赖查询 DSL（CORE-05，ArchUnit
 * {@code readSideDslOnlyInQueryLayer} 门禁）。
 *
 * <p>组装统一收敛在 {@code application/query/support} 静态 assembler（{@link OrderSummaryAssembler}），Handler
 * 内不写私有组装方法。
 */
@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {

  private final OrderReadPort orderReadPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<OrderDto> handle(OrderPageQuery query) {
    long tenantId = tenantProvider.currentTenantId();

    Long customerId = query.customerId();
    OrderStatus status = parseStatus(query.status());

    int pageNum = query.pageNum() != null ? query.pageNum() : 1;
    int pageSize = query.pageSize() != null ? query.pageSize() : 10;

    PageResult<OrderHeadRow> page =
        orderReadPort.findOrderPage(tenantId, customerId, status, pageNum, pageSize);

    List<OrderDto> records =
        page.getRecords().stream().map(OrderSummaryAssembler::fromRow).toList();
    return PageResult.of(records, page.getTotal(), pageNum, pageSize);
  }

  /**
   * 解析状态入参：非法值转 {@link BizException}（显式 400 + {@code BP_ORDER_STATUS_INVALID}）。
   *
   * <p>直接 {@code OrderStatus.valueOf} 会抛 {@code IllegalArgumentException}，被全局处理器兜底成 5xx——
   * 把「调用方传错参数」报成「服务端故障」，既误导排查也会污染告警。注意：必须走 {@code BizException}(HTTP 状态, ...) 这一载码构造器；{@code
   * BizException(String)} 的默认码是 <strong>500</strong>，而 {@code InvalidRequestException} 更是未被 {@code
   * GlobalExceptionHandler} 识别， 两者都会把 400 变成 500。
   */
  private static OrderStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new BizException(400, BlueprintErrorCodes.ORDER_STATUS_INVALID + ": " + status);
    }
  }
}
