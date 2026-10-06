package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ChannelProductApplicationService;
import com.bone.blueprint.application.query.dto.ChannelProductDto;
import com.bone.blueprint.domain.model.channel.ChannelProduct;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道商品 Web 接口 —— 多渠道商品上架 / 下架 / 库存广播。
 *
 * <p><b>授权</b>：上架是把商品推到外部平台销售的动作，直接影响对外经营，写端点必须收 {@code commerce:product:write}。
 */
@Tag(name = "渠道商品管理", description = "多渠道商品上架/下架与库存同步")
@RestController
@RequestMapping("/api/v1/channel-products")
@RequiredArgsConstructor
public class ChannelProductController {

  private final ChannelProductApplicationService channelProductApplicationService;

  @Operation(summary = "渠道商品分页列表")
  @PreAuthorize("hasAuthority('commerce:product:read')")
  @GetMapping
  public ApiResponse<PageResult<ChannelProductDto>> page(
      @RequestParam(required = false) String channelCode,
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) String listingStatus,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        channelProductApplicationService.page(channelCode, productId, listingStatus, page, size));
  }

  @Operation(summary = "商品上架到渠道")
  @PreAuthorize("hasAuthority('commerce:product:write')")
  @PostMapping
  public ApiResponse<ChannelProductDto> publishProduct(@Valid @RequestBody PublishProductReq req) {
    return ApiResponse.success(
        channelProductApplicationService.publishProduct(
            req.channelCode(), req.productId(), req.productName(), req.listingPrice()));
  }

  @Operation(summary = "商品从渠道下架")
  @PreAuthorize("hasAuthority('commerce:product:write')")
  @PostMapping("/delist")
  public ApiResponse<ChannelProductDto> delistProduct(@Valid @RequestBody DelistProductReq req) {
    return ApiResponse.success(
        channelProductApplicationService.delistProduct(req.channelCode(), req.productId()));
  }

  /**
   * 库存广播：把指定商品的库存广播到<strong>所有已上架渠道</strong>。
   *
   * <p>为何是广播而非单渠道：多渠道共享实物库存，只同步一个渠道会在其他渠道留下过期库存导致超卖。
   *
   * <p><b>语义变更（Outbox 化）</b>：本端点现在<strong>只入队</strong>并立即推进一轮中继， 返回的是「已入队的渠道商品」， 不再是「渠道已同步成功」。
   * 中继失败会自动退避重试，耗尽转死信并可在广播任务页人工重试。
   */
  @Operation(summary = "库存广播到全部已上架渠道（异步投递，返回入队结果）")
  @PreAuthorize("hasAuthority('commerce:product:write')")
  @PostMapping("/sync-inventory")
  public ApiResponse<List<ChannelProductDto>> syncInventory(
      @RequestParam Long productId, @RequestParam(defaultValue = "0") int stock) {
    List<ChannelProduct> enqueued =
        channelProductApplicationService.syncInventoryToAllChannels(productId, stock);
    List<ChannelProductDto> data = enqueued.stream().map(ChannelProductDto::from).toList();
    return ApiResponse.success(data);
  }

  /** 上架请求。 */
  public record PublishProductReq(
      @NotBlank(message = "渠道码不能为空") String channelCode,
      @NotNull(message = "商品ID不能为空") Long productId,
      @NotBlank(message = "商品名称不能为空") String productName,
      @NotNull(message = "挂牌价不能为空") @Positive(message = "挂牌价必须大于 0") BigDecimal listingPrice) {}

  /** 下架请求。 */
  public record DelistProductReq(
      @NotBlank(message = "渠道码不能为空") String channelCode,
      @NotNull(message = "商品ID不能为空") Long productId) {}
}
