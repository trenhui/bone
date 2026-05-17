# Bone 缓存规范

> **文档性质**：Redis / Caffeine 的 Key 设计、TTL、租户隔离与防护策略。  
> **更新**：2026-05-17  
> **关联**：[Bone-多租户规范.md](./Bone-多租户规范.md)、Redisson / Caffeine（各模块）

---

## 1. 技术选型

| 场景 | 推荐 |
|------|------|
| 分布式会话、幂等、跨实例 | Redis（Redisson） |
| 进程内热点、路由定义 | Caffeine（extension-sdk 等） |
| 禁止 | 无 TTL 的无限增长 Key |

---

## 2. Key 命名

```
bone:{tenantId}:{domain}:{resource}:{id}[:{facet}]
```

| 段 | 示例 |
|----|------|
| 前缀 | 固定 `bone:` |
| tenantId | `default` |
| domain | `iam`, `extension` |
| resource | `token`, `idempotency`, `route` |

**禁止**：无租户前缀的全局业务 Key（平台配置除外，须 `bone:platform:`）。

---

## 3. TTL

| 类型 | TTL 建议 |
|------|----------|
| 幂等键 | 24h（与 API Idempotency-Key 一致） |
| 会话/Token 黑名单 | 与 Token 过期一致 |
| 路由/元数据缓存 | 1h，变更时主动失效 |
| 验证码 | 5–10 min |

---

## 4. 一致性

| 规则 | 说明 |
|------|------|
| 写后删 | 更新 DB 后删除/失效相关缓存 |
| 禁止 | 仅依赖 TTL 保证读一致（强一致读须直查 DB） |
| 穿透 | 空值短 TTL 或布隆过滤器（高 QPS 列表） |
| 击穿 | 热点 Key 互斥锁或 singleflight |
| 雪崩 | TTL 加随机抖动 |

---

## 5. 序列化

| 规则 | 说明 |
|------|------|
| 格式 | JSON 或 JDK 序列化（内部）；跨语言须 JSON |
| 禁止 | 缓存含密码、完整 Token |
| 版本 | 结构变更时 Key 加 `:v2` 或清库 |

---

## 6. 检查清单

- [ ] Key 含 `tenantId`  
- [ ] 所有 Key 有 TTL  
- [ ] 写路径有失效逻辑  
- [ ] 无敏感信息入缓存  

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版 |
