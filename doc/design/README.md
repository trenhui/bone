# 详细设计文档（`doc/design`）

| 路径 | 说明 |
|------|------|
| [modules/README.md](./modules/README.md) | **模块详设索引**（编号详设 + 横切方案；`review-report-*` 封箱原地） |
| [**lowcode/README.md**](./lowcode/README.md) | **低代码 Mode B 专题包**（迭代 PRD + runtime / cases / ui） |
| [assets/](./assets/) | 配图（如运行时拓扑 SVG） |
| [AI-夜间设计复核工作流方案.md](./AI-夜间设计复核工作流方案.md) | 过程稿（nightly）；**勿迁入 modules** |
| [../ai-product/](../ai-product/) | **AI 文档区**（AI 能力中台三件套 + procuraAI / synapClaim / synapEquity 产品包，2026-10-14 自本目录迁出） |
| [费用报销系统-设计文档.md](./lowcode/cases/费用报销系统-设计文档.md) | **费用报销域独立系统设计**（双实体/7 态状态机/行级权限/出金契约；基于代码真源 2026-10-23） |

**上游**：[`doc/prd/`](../prd/) · **架构**：[`doc/architecture/`](../architecture/) · **升级草案**：[`doc/plans/`](../plans/) · **端口真源**：[wiki/03](../wiki/03-本地开发与构建.md)

## 运行工件区（封箱 · 勿随意改路径）

下列路径被 `scripts/prompts/02-nightly-design-review.md` 等写死，**只改内容、不改路径/文件名**：

| 工件 | 用途 |
|------|------|
| `_global-contracts.yaml` | 夜间锚定契约清单 |
| `_review-status.yaml` | 轮换状态 |
| `_nightly-log.md` | 夜间日志 |
| `_pending-fixes-*.md` / `.patch` | 待修补丁稿 |
| `bone-pages-spec.html` | 页面规格辅助 |
| `modules/review-report-*.md` | 各模块夜间复核输出 |

## 已删除空壳

原 `design/ui/`、`design/cases/`（仅跳转 README）已删除 → 一律进 [`lowcode/`](./lowcode/)。
