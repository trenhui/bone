package com.bone.blueprint.application.command.cmd;

import lombok.Data;

@Data
public class CancelOrderCommand {
    private Long orderId;
}
