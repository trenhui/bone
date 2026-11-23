# Brainstorm Summary — procuraai-contract-reviewed-event-chain

状态：候选方案已形成，待 Design 提案门用户确认（2026-11-23）。

## 目标与约束（已确认，来自批次清单 + Open 产物）

把 ContractReviewed 从进程内 ConcurrentHashMap 直连升级为持久化 Outbox→Inbox→DLQ 幂等消费闭环；三类负向探针；按 A10.17 同步三入口。POC 深度用户已选"完整闭环 + 三入口接入"。不接生产 MQ、不执行生产库操作、不扩展事件类型。

## 源码事实（已核实）

- `ContractReviewedConsumer`：`ConcurrentHashMap` 存结论、`ConcurrentHashMap.newKeySet()` 存隔离，`consume()` 以 reviewId `putIfAbsent` 幂等；由 `ContractReviewApplicationService.override` 进程内直连。
- `ContractReviewedEvent`：严格 7 字段 record，注释声明不承载 output 原文/合同正文/PII。
- 现有 `procurement/contract-ai-review` spec 有"审查留痕、幂等与追溯链"requirement，但仅内存态。
- persistence 门禁（`tools/check_procura_persistence.py:21-26`）守护 6 表（含 `t_proc_contract_review`/`t_proc_review_trace`/`t_proc_global_kill_switch`），文案"6 表 / 双唯一键 / 9 端口持久化实现"。
- A10.17：`GATE_KEYS` 9 key（`docGate/docProbe/evalGate/evalProbe/asyncapiCheck/frontmatterGate/frontmatterProbe/persistenceGate/persistenceProbe`），`FULL_GATE_KEYS` 精确比对三入口；三入口 = `ci.yml`/`scripts/check.sh`/`check-procura-ci.sh`。

## 方案取舍（候选，逐项含否决备选）

见 `design.md` 的 6 项 Decision。核心：三表 + `tenant_id+review_id` 唯一约束幂等 + 租户 fail-closed + DLQ 按 review_id 重放 + payload 7 字段不变 + 三入口 L3 接入。每项均记录了否决方案（复用 review_trace 做去重、SELECT FOR UPDATE、信任事件 tenant、无限自动重试、只做本地门禁不接三入口）。

## Spec Patch 评估

无——design.md 的 4 条新 Requirement 与 delta spec 完全一致，未改动需求行为，仅新增可观察场景。不触发 Spec Patch。

## 开放问题

无影响方案/spec/任务拆分的问题。实现层细节（Outbox 扫描周期、DLQ 重放入口形态）留给 Build 计划，不改变行为契约。
