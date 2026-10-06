package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ShipmentApplicationService;
import com.bone.blueprint.application.query.dto.ShipmentDto;
import com.bone.blueprint.application.query.dto.ShipmentTraceDto;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发货物流 Web 接口。
 *
 * <p><b>授权</b>：发货回传渠道会改变平台侧订单履约状态，属资金/履约相关写操作， 收 {@code commerce:shipment:write}。
 */
@Tag(name = "发货物流管理", description = "发货单创建、发货、签收与物流轨迹同步")
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

  private final ShipmentApplicationService shipmentApplicationService;

  @Operation(summary = "发货单分页列表")
  @PreAuthorize("hasAuthority('commerce:shipment:read')")
  @GetMapping
  public ApiResponse<PageResult<ShipmentDto>> page(
      @RequestParam(required = false) String channelCode,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(shipmentApplicationService.page(channelCode, status, page, size));
  }

  @Operation(summary = "发货单详情")
  @PreAuthorize("hasAuthority('commerce:shipment:read')")
  @GetMapping("/{shipmentId}")
  public ApiResponse<ShipmentDto> detail(@PathVariable Long shipmentId) {
    return ApiResponse.success(shipmentApplicationService.detail(shipmentId));
  }

  @Operation(summary = "按订单查发货单")
  @PreAuthorize("hasAuthority('commerce:shipment:read')")
  @GetMapping("/by-order/{orderId}")
  public ApiResponse<ShipmentDto> byOrder(@PathVariable Long orderId) {
    return ApiResponse.success(shipmentApplicationService.byOrder(orderId));
  }

  @Operation(summary = "创建发货单（幂等：一单一份）")
  @PreAuthorize("hasAuthority('commerce:shipment:write')")
  @PostMapping
  public ApiResponse<ShipmentDto> create(@Valid @RequestBody CreateShipmentReq req) {
    return ApiResponse.success(
        shipmentApplicationService.create(
            req.orderId(),
            req.channelCode(),
            req.receiverName(),
            req.receiverPhone(),
            req.receiverAddress()));
  }

  @Operation(summary = "发货（生成运单号并回传渠道）")
  @PreAuthorize("hasAuthority('commerce:shipment:write')")
  @PostMapping("/{shipmentId}/ship")
  public ApiResponse<ShipmentDto> ship(
      @PathVariable Long shipmentId, @Valid @RequestBody ShipReq req) {
    return ApiResponse.success(
        shipmentApplicationService.ship(shipmentId, req.logisticsCompany(), req.trackingNo()));
  }

  @Operation(summary = "签收")
  @PreAuthorize("hasAuthority('commerce:shipment:write')")
  @PostMapping("/{shipmentId}/sign")
  public ApiResponse<ShipmentDto> sign(@PathVariable Long shipmentId) {
    return ApiResponse.success(shipmentApplicationService.sign(shipmentId));
  }

  @Operation(summary = "从渠道拉取物流轨迹并落库")
  @PreAuthorize("hasAuthority('commerce:shipment:write')")
  @PostMapping("/{shipmentId}/traces/sync")
  public ApiResponse<List<ShipmentTraceDto>> syncTrace(@PathVariable Long shipmentId) {
    return ApiResponse.success(shipmentApplicationService.syncTrace(shipmentId));
  }

  @Operation(summary = "本地物流轨迹列表")
  @PreAuthorize("hasAuthority('commerce:shipment:read')")
  @GetMapping("/{shipmentId}/traces")
  public ApiResponse<List<ShipmentTraceDto>> traces(@PathVariable Long shipmentId) {
    return ApiResponse.success(shipmentApplicationService.traces(shipmentId));
  }

  /** 创建发货单请求。 */
  public record CreateShipmentReq(
      @NotNull(message = "订单ID不能为空") Long orderId,
      String channelCode,
      String receiverName,
      String receiverPhone,
      String receiverAddress) {}

  /** 发货请求。 */
  public record ShipReq(
      String logisticsCompany, @NotBlank(message = "运单号不能为空") String trackingNo) {}
}
