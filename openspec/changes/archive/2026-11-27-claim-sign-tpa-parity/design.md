# Design: claim-sign-tpa-parity

## 决策

1. **不照搬源站脏数据字段**：参考站 `insuranceCompany` 存在编码值与乱码混用（如 `412312`）；
   Bone 只存可读名称，编码兼容由导入脚本在写入前归一，不进领域模型。
2. **查询走 Criteria 而非自定义 SQL**：现有 `findBatchPage` 已用 Criteria 分页，扩展
   `.like/.eq/.ge/.le` 即可，避免新增 SQL 模板与租户锚点风险。
3. **时间窗用 Instant 解析**：参数接受 ISO-8601（前端 `Date.toISOString()`），服务端拒绝非法格式
   并返回明确的 400，不做隐式宽松解析。
4. **明细视图只读**：沿用 `findItemsByBatchId`，批次明细规模小（单批 < 100），不引入分页复杂度；
   前端按赔案号排序。
5. **详情抽屉 Promise.allSettled**：轨迹、明细、建案结果并行加载，局部失败不拖垮整页（延续上一 change 的修复）。
6. **状态机与自动建案零改动**：本次仅扩展查询与展示，不触碰四态迁移、`AFTER_COMMIT` 订阅与幂等键。

## 数据库

0147 迁移：`t_claim_sign_batch` 增 `insurance_company`、`insurance_subsidiary`、`insuring_agency`、
`emergency`；`t_claim_sign_batch_item` 增 `identity_type`、`counting_result`、`image_count`、
`image_upload_count`、`image_upload_time`、`image_uploader`。全部可空，幂等 ADD COLUMN。
