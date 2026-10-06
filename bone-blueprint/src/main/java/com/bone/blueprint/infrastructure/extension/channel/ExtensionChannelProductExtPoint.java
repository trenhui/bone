package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelProductExtPoint;
import com.bone.engine.extension.api.annotation.ExtensionPoint;

/** 渠道商品扩展点（技术接口），承载 {@link ExtensionPoint} 标记。 */
@ExtensionPoint(
    name = "渠道商品扩展点",
    description = "各销售渠道的商品上架/下架与库存同步（淘宝/京东/抖音/拼多多）",
    version = "1.0.0")
public interface ExtensionChannelProductExtPoint extends ChannelProductExtPoint {}
