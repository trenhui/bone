package com.bone.blueprint.adapter.rpc.dto;

import lombok.Data;

@Data
public class CreateOrderRpcResp {
    private Long orderId;
    private String status;
    private boolean success;
    private String errorMsg;
}
