package com.bone.architecture.fixture.beans.colliding.rpc;

import org.springframework.stereotype.Component;

/**
 * 规则单测夹具（E-13.0 反例 · rpc 侧）：与 {@code beans.colliding.web.OrderController} 同名同默认 bean 名。
 *
 * <p>本对夹具用于证明 {@code springComponentBeanNamesMustBeUnique} 能识别「类名不同包、bean 名相同」。 非产品代码，仅供 {@code
 * BoneDddArchRulesVerificationTest} 使用。
 */
@Component
public class OrderController {}
