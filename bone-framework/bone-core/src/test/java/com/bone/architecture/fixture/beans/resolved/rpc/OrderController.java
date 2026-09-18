package com.bone.architecture.fixture.beans.resolved.rpc;

import org.springframework.stereotype.Component;

/**
 * 规则单测夹具（E-13.0 正例 · rpc 侧）：同名类改用显式 bean 名，协议标记落在 DI 标识而非类名。
 *
 * <p>对应 E-13.0「标记下沉到 DI 标识」的第一种机制；真实代码用 {@code @RestController("rpcOrderController")}， 夹具用
 * {@code @Component} 保持依赖最小。非产品代码，仅供 {@code BoneDddArchRulesVerificationTest} 使用。
 */
@Component("rpcOrderController")
public class OrderController {}
