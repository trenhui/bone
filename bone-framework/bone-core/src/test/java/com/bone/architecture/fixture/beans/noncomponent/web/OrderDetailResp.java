package com.bone.architecture.fixture.beans.noncomponent.web;

/**
 * 规则单测夹具（E-13.0 正例 · web 侧非组件）：与 {@code beans.noncomponent.rpc.OrderDetailResp} 同名同包并列， 但两者都不是
 * Spring 组件。
 *
 * <p>模拟真实形态：{@code adapter/web/dto/response} 与 {@code adapter/rpc/dto/response} 各有一个 {@code
 * OrderDetailResp}，DTO 不参与容器注册，故不构成 bean 名冲突。非产品代码，仅供 {@code BoneDddArchRulesVerificationTest} 使用。
 */
public class OrderDetailResp {}
