package com.bone.synap.settlement.adapter.spring;

import com.bone.synap.settlement.adapter.jdbc.JdbcBackedPorts;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;

import javax.sql.DataSource;

/**
 * 理算引擎装配工厂（无框架依赖，可在任意上下文使用）。
 *
 * 在 Bone 平台内，BONE-AI 各能力中心（C1 文档解析 / C2 规则编排 / C5 LLM 网关 /
 * C6 受控 RAG / C8 安全基线 / C9 提醒 / C10 集成三态灰度 / C11 额度台账）通过下列端口
 * 接入本引擎（六边形防腐，domain 不直接依赖任何实现）：
 *
 *   Ports.LedgerPort         -> C11 信任额度台账（占额 / 查剩余）
 *   Ports.TracePort          -> C4  快照审计（公式快照落库，可回放复算）
 *   Ports.CatalogPort        -> C6  受控 RAG / 主数据目录编码解析
 *   Ports.OutboxPort         -> 领域事件（最终一致，供对账 / 后督订阅）
 *   Ports.AccumulationPort   -> 跨案累计（年度免赔 / 年度限额 / 责任限额 + 预授权冻结），本工厂用 JDBC 实现
 *   Ports.SettlementStorePort-> 幂等结果存储（CAS 租约 + 按 claimId 查最新结论）
 *   Ports.PreAuthPort        -> 直付预授权生命周期，接 C10 通道
 *
 * Bone 中的 Spring 装配示意（依赖 bone-ai-* 与 spring-context，由平台侧提供，不在本模块强依赖）：
 * <pre>{@code
 * @Configuration
 * class SettlementConfig {
 *     @Bean SettlementEngine settlementEngine(DataSource ds, EngineFlags flags) {
 *         return SettlementEngineFactory.create(ds, flags);
 *     }
 * }
 * }</pre>
 */
public final class SettlementEngineFactory {

    private SettlementEngineFactory() { }

    /** 使用全部护栏开启（业界最佳实践默认）的引擎。 */
    public static SettlementEngine create(DataSource ds) {
        return create(ds, EngineFlags.allOn());
    }

    /**
     * 装配一个挂载 JDBC 持久化端口的理算引擎。
     * 同一 JdbcBackedPorts 实例实现全部 7 个端口（纯 JDBC，无框架依赖）。
     */
    public static SettlementEngine create(DataSource ds, EngineFlags flags) {
        JdbcBackedPorts ports = new JdbcBackedPorts(ds);
        return new SettlementEngine(ports, ports, ports, ports, ports, ports, ports, flags);
    }

    /** 用给定的端口实现装配引擎（如单测用 InMemoryPorts）。 */
    public static SettlementEngine create(Ports.LedgerPort ledger, Ports.TracePort trace,
                                         Ports.CatalogPort catalog, Ports.OutboxPort outbox,
                                         Ports.AccumulationPort accumulation,
                                         Ports.SettlementStorePort store, Ports.PreAuthPort preAuth,
                                         EngineFlags flags) {
        return new SettlementEngine(ledger, trace, catalog, outbox, accumulation, store, preAuth, flags);
    }
}
