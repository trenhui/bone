package com.bone.architecture.fixture.beans.noncomponent.rpc;

/**
 * 规则单测夹具（E-13.0 正例 · rpc 侧非组件）：与 {@code beans.noncomponent.web.OrderDetailResp} 同名。
 *
 * <p>成对证明规则只约束 Spring 组件——同名 DTO 不会、也不该被 {@code springComponentBeanNamesMustBeUnique} 判为冲突。非产品代码，仅供
 * {@code BoneDddArchRulesVerificationTest} 使用。
 */
public class OrderDetailResp {}
