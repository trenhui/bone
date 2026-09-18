package com.bone.architecture.fixture.beans.colliding.web;

import org.springframework.stereotype.Component;

/**
 * 规则单测夹具（E-13.0 反例 · web 侧）：与 {@code beans.colliding.rpc.OrderController} 同名。
 *
 * <p>模拟「包路径已表达协议、两个协议合法复用同一业务类名，但都没写显式 bean 名」的场景——Spring 默认 {@code AnnotationBeanNameGenerator}
 * 会给两者都注册 {@code orderController}，启动期抛 {@code ConflictingBeanDefinitionException}。非产品代码，仅供 {@code
 * BoneDddArchRulesVerificationTest} 使用。
 */
@Component
public class OrderController {}
