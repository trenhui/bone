
package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.req.CancelOrderRequest;
import com.bone.blueprint.adapter.web.dto.req.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.resp.OrderDetailResponse;
import com.bone.blueprint.adapter.web.dto.resp.OrderPageResponse;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.core.result.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderAssembler {

    public CreateOrderCommand toCreateOrderCommand(CreateOrderRequest request) {
        List<CreateOrderCommand.OrderItem> items = request.getItems().stream()
                .map(item -> CreateOrderCommand.OrderItem.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        CreateOrderCommand.ShippingAddress address = CreateOrderCommand.ShippingAddress.builder()
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .province(request.getProvince())
                .city(request.getCity())
                .district(request.getDistrict())
                .detail(request.getDetail())
                .postalCode(request.getPostalCode())
                .build();

        return CreateOrderCommand.builder()
                .customerId(request.getCustomerId())
                .items(items)
                .shippingAddress(address)
                .remark(request.getRemark())
                .build();
    }

    public CancelOrderCommand toCancelOrderCommand(String orderId, CancelOrderRequest request) {
        return CancelOrderCommand.builder()
                .orderId(orderId)
                .reason(request.getReason())
                .build();
    }

    public OrderPageQuery toOrderPageQuery(int pageNo, int pageSize, String customerId, String status) {
        return OrderPageQuery.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .customerId(customerId)
                .status(status)
                .build();
    }

    public OrderDetailQuery toOrderDetailQuery(String orderId) {
        return OrderDetailQuery.builder()
                .orderId(orderId)
                .build();
    }

    public OrderDetailResponse toOrderDetailResponse(OrderDto dto) {
        List<OrderDetailResponse.OrderItem> items = dto.getItems().stream()
                .map(item -> OrderDetailResponse.OrderItem.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        OrderDetailResponse.ShippingAddress address = OrderDetailResponse.ShippingAddress.builder()
                .recipientName(dto.getShippingAddress().getRecipientName())
                .phone(dto.getShippingAddress().getPhone())
                .province(dto.getShippingAddress().getProvince())
                .city(dto.getShippingAddress().getCity())
                .district(dto.getShippingAddress().getDistrict())
                .detail(dto.getShippingAddress().getDetail())
                .postalCode(dto.getShippingAddress().getPostalCode())
                .build();

        return OrderDetailResponse.builder()
                .id(dto.getId())
                .customerId(dto.getCustomerId())
                .items(items)
                .totalAmount(dto.getTotalAmount())
                .status(dto.getStatus())
                .statusDescription(dto.getStatusDescription())
                .shippingAddress(address)
                .remark(dto.getRemark())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .paidTime(dto.getPaidTime())
                .shippedTime(dto.getShippedTime())
                .completedTime(dto.getCompletedTime())
                .cancelledTime(dto.getCancelledTime())
                .build();
    }

    public PageResult<OrderPageResponse> toOrderPageResponse(PageResult<OrderDto> pageResult) {
        List<OrderPageResponse> responses = pageResult.getItems().stream()
                .map(dto -> OrderPageResponse.builder()
                        .id(dto.getId())
                        .customerId(dto.getCustomerId())
                        .totalAmount(dto.getTotalAmount())
                        .status(dto.getStatus())
                        .statusDescription(dto.getStatusDescription())
                        .recipientName(dto.getShippingAddress().getRecipientName())
                        .phone(dto.getShippingAddress().getPhone())
                        .createTime(dto.getCreateTime())
                        .updateTime(dto.getUpdateTime())
                        .build())
                .collect(Collectors.toList());

        return PageResult.<OrderPageResponse>builder()
                .items(responses)
                .total(pageResult.getTotal())
                .pageNo(pageResult.getPageNo())
                .pageSize(pageResult.getPageSize())
                .build();
    }
}

