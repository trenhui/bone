package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelFulfillmentExtPoint;
import com.bone.engine.extension.api.annotation.ExtensionPoint;

/**
 * 渠道履约扩展点（技术接口）——承载 {@link ExtensionPoint} 标记。
 *
 * <p><b>为何业务接口与技术接口要分离</b>：{@link ChannelFulfillmentExtPoint} 放在 {@code domain} 包,
 * 必须保持零框架依赖（ArchUnit {@code domainMustNotDependOnOuterLayers}）；而 {@code @ExtensionPoint} 是扩展引擎 SDK
 * 的注解，属于框架。故在基础设施层派生一个只加注解的子接口—— 领域契约与框架装配解耦，Domain 层可以脱离扩展引擎独立测试。
 */
@ExtensionPoint(
    name = "渠道履约扩展点",
    description = "各销售渠道的发货回传与物流轨迹查询（淘宝/京东/抖音/拼多多）",
    version = "1.0.0")
public interface ExtensionChannelFulfillmentExtPoint extends ChannelFulfillmentExtPoint {}
