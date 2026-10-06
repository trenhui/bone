package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.application.ChannelOrderApplicationService;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderLine;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道订单 Web 接口 —— 模拟渠道推送/拉取后落成内部订单。
 *
 * <p><b>授权</b>：拉单会真实占用库存并产生订单，收 {@code commerce:channel:write}。
 *
 * <p><b>为何用 POST 而不是 GET</b>：这是改变系统状态的动作（建单 + 占库存）， 用 GET 会被浏览器预取、爬虫、网关缓存重复触发——幂等键能兜住重复，但把写操作暴露成
 * 可缓存的 GET 本身就是设计错误。
 */
@Tag(name = "渠道订单接入", description = "多渠道（淘宝/京东/抖音/拼多多）订单拉取与落单")
@RestController
@RequestMapping("/api/v1/channel-orders")
@RequiredArgsConstructor
public class ChannelOrderController {

  private final ChannelOrderApplicationService channelOrderApplicationService;

  /** 拉取渠道订单并落成内部订单（幂等：重复推送返回同一订单ID）。 */
  @Operation(summary = "拉取渠道订单并落单")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping("/pull")
  public ApiResponse<Long> pull(@Valid @RequestBody PullChannelOrderReq req) {
    return ApiResponse.success(
        channelOrderApplicationService.pullAndCreate(
            // tenantId 传 null：Web 上下文在此处尚未建立，由 ChannelOrderApplicationService 经
            // TenantPort 统一补齐（显式入参而非 ThreadLocal，扩展点路由同构）。
            new ChannelOrderContext(
                null,
                req.channelCode(),
                req.channelOrderNo(),
                req.buyerNick(),
                req.buyerId(),
                req.payAmount(),
                req.receiverName(),
                req.receiverPhone(),
                req.receiverAddress(),
                req.lines().stream()
                    .map(
                        l ->
                            new ChannelOrderLine(
                                l.outerSkuId(), l.title(), l.quantity(), l.unitPrice()))
                    .toList())));
  }

  /**
   * 发货信息回传渠道（人工/补偿触发）。
   *
   * <p><b>为何必须由调用方传承运商与运单号</b>：早期本端点只收渠道订单号，运单号由服务端写死， 导致回传给渠道的永远是同一串假单号——渠道展示的物流与实际不符，而接口返回成功。
   * 服务端不掌握真实运单号（它在发货环节录入），故只能由调用方提供。
   */
  @Operation(summary = "发货信息回传渠道（人工/补偿）")
  @PreAuthorize("hasAuthority('commerce:channel:write')")
  @PostMapping("/{channelCode}/{channelOrderNo}/ack")
  public ApiResponse<ChannelShipmentResult> ack(
      @PathVariable String channelCode,
      @PathVariable String channelOrderNo,
      @RequestBody @Valid AckShipmentReq request) {
    return ApiResponse.success(
        channelOrderApplicationService.ackToChannel(
            channelCode, channelOrderNo, request.logisticsCompany(), request.trackingNo()));
  }

  /** 发货回传请求体：承运商与真实运单号。 */
  public record AckShipmentReq(
      @NotBlank(message = "物流公司不能为空") String logisticsCompany,
      @NotBlank(message = "运单号不能为空") String trackingNo) {}

  /** 渠道订单拉取请求（模拟渠道推送的原始报文）。 */
  public record PullChannelOrderReq(
      @NotBlank(message = "渠道码不能为空") String channelCode,
      @NotBlank(message = "渠道订单号不能为空") String channelOrderNo,
      /**
       * 渠道买家账号ID（淘宝 buyer_user_id / 京东 buyerdno / 抖音 buyer_second_id / 拼多多 user_id）。
       *
       * <p>留空则按「无买家身份」落单（内部客户维度退化为 0），而<strong>不是</strong>用昵称哈希冒充身份。
       */
      String buyerId,
      String buyerNick,
      BigDecimal payAmount,
      String receiverName,
      String receiverPhone,
      String receiverAddress,
      @NotEmpty(message = "订单明细不能为空") List<LineReq> lines) {

    /** 渠道明细行。 */
    public record LineReq(
        @NotBlank(message = "渠道SKU不能为空") String outerSkuId,
        String title,
        @NotNull(message = "数量不能为空") Integer quantity,
        @NotNull(message = "单价不能为空") BigDecimal unitPrice) {}
  }
}
