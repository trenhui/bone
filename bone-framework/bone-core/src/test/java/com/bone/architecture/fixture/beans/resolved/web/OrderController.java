package com.bone.architecture.fixture.beans.resolved.web;

import org.springframework.stereotype.Component;

/**
 * 规则单测夹具（E-13.0 正例 · web 侧）：与 {@code beans.resolved.rpc.OrderController} 同名，但用默认 bean 名。
 *
 * <p>与 rpc 侧成对，证明「同名类只要其中一个显式声明 bean 名即可共存」——这是 E-13.0 推荐的冲突消解形态。 非产品代码，仅供 {@code
 * BoneDddArchRulesVerificationTest} 使用。
 */
@Component
public class OrderController {}
