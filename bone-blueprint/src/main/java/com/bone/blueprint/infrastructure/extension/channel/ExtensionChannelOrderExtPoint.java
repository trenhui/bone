package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelOrderExtPoint;
import com.bone.engine.extension.api.annotation.ExtensionPoint;

/** 渠道订单扩展点（技术接口），承载 {@link ExtensionPoint} 标记。 */
@ExtensionPoint(
    name = "渠道订单扩展点",
    description = "各销售渠道的订单拉取归一化与状态回传（淘宝/京东/抖音/拼多多）",
    version = "1.0.0")
public interface ExtensionChannelOrderExtPoint extends ChannelOrderExtPoint {}
