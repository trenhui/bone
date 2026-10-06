package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.InventoryApplicationService;
import com.bone.blueprint.application.query.dto.InventoryDto;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * 库存 Web 接口。
 *
 * <p><b>授权</b>：库存是超卖的唯一拦截点，写端点（入库/扣减/预留）若任何人可调， 就能绕过下单流程直接改库存篡改可售量。故读写分离收权限。
 */
@Tag(name = "库存管理", description = "多渠道共享库存的查询、入库、扣减与预留")
@RestController
@RequestMapping("/api/v1/inventories")
@RequiredArgsConstructor
public class InventoryController {

  private final InventoryApplicationService inventoryApplicationService;

  @Operation(summary = "库存分页列表")
  @PreAuthorize("hasAuthority('commerce:inventory:read')")
  @GetMapping
  public ApiResponse<PageResult<InventoryDto>> page(
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) Boolean lowStockOnly,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        inventoryApplicationService.page(productId, lowStockOnly, page, size));
  }

  @Operation(summary = "库存详情")
  @PreAuthorize("hasAuthority('commerce:inventory:read')")
  @GetMapping("/{productId}")
  public ApiResponse<InventoryDto> detail(
      @PathVariable Long productId, @RequestParam(required = false) String warehouseCode) {
    return ApiResponse.success(inventoryApplicationService.detail(productId, warehouseCode));
  }

  @Operation(summary = "建库存/入库（已存在则累加可用量）")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/receive")
  public ApiResponse<InventoryDto> receive(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.receive(
            req.productId(), req.productName(), req.warehouseCode(), req.quantity()));
  }

  @Operation(summary = "扣减可用库存（报损/纠错）")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/deduct")
  public ApiResponse<InventoryDto> deduct(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.deduct(req.productId(), req.warehouseCode(), req.quantity()));
  }

  @Operation(summary = "预留库存（下单占用）")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/reserve")
  public ApiResponse<InventoryDto> reserve(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.reserve(req.productId(), req.warehouseCode(), req.quantity()));
  }

  @Operation(summary = "确认出库（预留转实际出库）")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/confirm")
  public ApiResponse<InventoryDto> confirm(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.confirm(req.productId(), req.warehouseCode(), req.quantity()));
  }

  @Operation(summary = "释放预留（取消订单/超时关单）")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/release")
  public ApiResponse<InventoryDto> release(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.release(req.productId(), req.warehouseCode(), req.quantity()));
  }

  @Operation(summary = "设置安全库存阈值")
  @PreAuthorize("hasAuthority('commerce:inventory:write')")
  @PostMapping("/safety-stock")
  public ApiResponse<InventoryDto> setSafetyStock(@Valid @RequestBody StockChangeReq req) {
    return ApiResponse.success(
        inventoryApplicationService.setSafetyStock(
            req.productId(), req.warehouseCode(), req.quantity()));
  }

  /** 库存变更请求；{@code productName} 仅入库场景使用。 */
  public record StockChangeReq(
      @NotNull(message = "商品ID不能为空") Long productId,
      String productName,
      String warehouseCode,
      @NotNull(message = "数量不能为空") @Positive(message = "数量必须为正") Integer quantity) {}
}
