# 详细设计文档（`doc/design`）

| 文档 | 说明 |
|------|------|
| [modules/README.md](./modules/README.md) | **模块详设索引**（控制台、元数据、主数据、集成、扩展、IAM、系统、Generator、SmartMeta） |
| [BONE-X-Studio-详细设计方案.md](./BONE-X-Studio-详细设计方案.md) | BONE X Studio 终局详设（文首含 **As-Is / Vision** 分层） |
| [国际化设计方案.md](./国际化设计方案.md) | 控制台多语言（zh-CN / en-US）详设：错误文案、locale 单源管道、枚举翻译、CI 门禁；含 v1.3 代码复核勘误。**§12 已落地 `bone-blueprint` 样板**（后端 errorCode 链路 + UTC 契约 + 前端语言包 + `scripts/check-i18n-sync.py`） |

**上游（做什么）**：[`doc/prd/`](../prd/) · **平台架构（怎么做）**：[`doc/architecture/`](../architecture/) · **默认端口真源**：[wiki/03-本地开发与构建.md](../wiki/03-本地开发与构建.md)（与各模块 `application.yml` 对照）
