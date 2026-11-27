# Verify 报告：claim-sign-tpa-parity

## 目标与证据来源

按用户要求深度参考普康 TPA `https://tpa-test.pukangbao.com/jobManage/groupInsuranceSignList`，
基于真实页面而非猜测定字段与交互。抓取产物（临时目录，非提交内容）：

- `/tmp/tpa_sign_deep/list.png` — 签收批次列表页
- `/tmp/tpa_sign_deep/detail/modal.png` — 批次详情页（点「批次详情」进入）
- `/tmp/tpa_sign_deep/detail/dom.json` — 详情页 13 列表头与 9 个查询控件
- `/tmp/tpa_sign_deep/002__...getProcessListPage.json` — `signList` 页面配置（14 个字段定义）
- `/tmp/tpa_sign_deep/detail/000__...getProcessListPage.json` — `signDetailList` 页面配置

参考站真实结构：

| 页面 | 结构 |
| --- | --- |
| 签收批次列表页 | 全部/线下签收/线上签收三段；8 个查询条件；16 列；新批次签收/搜索/重置；行操作批次详情/导入人员 |
| 签收批次详情页 | 批次号/赔案数/保险公司/保险分公司/投保公司/签收渠道/创建时间摘要；赔案 13 列；批量上传影像历史/上传影像/确认签收 |

## 差距与落地

| 差距 | 落地 |
| --- | --- |
| Bone 无服务端组合查询 | `ClaimSignPageReq` 扩展 13 个条件；Criteria `.likeIfPresent/.eq/.gte/.lte`；服务端真分页 |
| 缺保司三方与紧急程度 | 0147 迁移加 4 列；实体/DTO/列表列/详情摘要同步 |
| 缺赔案维度详情 | 新增 `GET /sign/batches/{batchId}/items` + 明细 DTO；抽屉内三 Tab（赔案/轨迹/建案） |
| 缺影像要素字段 | 明细加证件类型/清点张数/影像数量/上传次数/上传时间/操作人员 6 列 |
| 状态/渠道展示不直观 | 渠道中文映射；`UNKNOWN` 读取期兜底为 `-`；三段来源 Tab 对齐参考站 |
| 源站脏数据 | 回填脚本只写含中文的可读名称，编码值与 UUID 跳过 |

保持四态状态机、`AFTER_COMMIT` 自动建案、幂等键与实名明文口径不变（仅扩展查询与展示）。

## 验证结果

- 后端全量：`Tests run: 1728, Failures: 0, Errors: 0, Skipped: 8`（1720 通过）
- 后端新增：`ClaimSignTpaParityTest` 5/5（含「查询条件全空不抛 NPE」回归）
- 前端：`tsc --noEmit` 通过；`CaseSignPage.test.tsx` 13/13（新增服务端查询与详情并发加载护栏）
- 静态门禁：迁移清单 `order 1..153` 连续；init 覆盖 114/114；DDL 文档同步；架构/契约/前端响应契约；影响分析 `1/1 端点已覆盖`
- 真机 API：`batchNo=264203068` → total=1，返回保险公司/分公司/创建时间；`GET .../items` → 4 条实名赔案明细
- 真机浏览器（`:3010/sclaim/claim/sign`）：三段 Tab、6 个新列、5 个新查询控件均可见；
  批次号筛选后 1 行；详情抽屉展示 4 个赔案、批次摘要与三个 Tab；console 错误 0
- 截图：`/tmp/claim-parity-screens/list.png`、`filtered.png`、`detail.png`

## 过程修复（真实联调暴露）

1. 空查询条件触发 `String.trim()` NPE（Criteria 的带 condition 重载不短路参数表达式）
   → 改用 `likeIfPresent`，并补「全空参数」回归测试。
2. `SqlTemplateEntityColumnCoverageTest` 报明细 SQL 漏列
   → `findItemsByBatchId.sql` 补齐 6 个新列投影，避免实体字段恒 null 静默失效。
3. E2E 脚本登录路径 `/api/v1/auth/login` 实际 404
   → 改为真实端点 `/api/v1/iam/login`。
4. 历史导入 `signChannel='UNKNOWN'` 在列表显示为 UNKNOWN
   → 前端读取期兜底 `-`，同时映射 EXPRESS/COUNTER/API_PUSH 为中文。

## 边界

- 0147 迁移脚本已入库并登记 manifest；本地开发库为浏览器验证直接执行了等价幂等加列，
  未手工写 `schema_migration_journal`（避免库改账本无记录），部署仍需走正式迁移。
- 源站 23 批中部分 `insuranceCompany` 是编码/乱码，回填只取中文名；无中文名的保持 NULL 显示 `-`。
- 沿用上一 change 的实名明文裁决；本次未扩大到新字段。
