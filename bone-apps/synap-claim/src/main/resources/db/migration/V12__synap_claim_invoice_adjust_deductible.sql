-- synap-claim 第十二批 DDL 变更：保险责任新增「发票级免赔 / 理算级免赔」两列（GAP② / GAP⑥ 落地）
--
-- 一、t_adj_liability 新增 invoice_deduct / adjust_deduct
--
-- 补的是什么洞：参考实现（reference-impl S 链）的理算顺序是
--   「原始费用 → 目录筛选 → 发票级免赔 → 第三方已付抵扣 → 年度免赔 → 比例 → 理算级免赔 → 取整 → 多维控额」。
--   生产引擎此前只有「年度免赔」一层（GAP④，已 SUPPORTED），缺「发票级免赔」（比例前、按费用池分摊）
--   与「理算级免赔」（比例后、二次绝对额扣减）两层，对应理算语义缺口清单的 GAP② / GAP⑥。
--
-- 两列语义：
--   invoice_deduct：在比例之前、年度免赔之前，对各费用池基数按占比分摊一次绝对额扣减；
--   adjust_deduct：在比例之后、控额之前，对理算金额再做一次绝对额扣减。
--   二者与年度免赔是三层独立免赔，彼此不共享额度、不参与跨案累计（仅本赔案一次）。
--
-- 为什么 NOT NULL DEFAULT 0：责任配置是版本化实体，已生效版本不可改（§4.1），新增列必须保证历史行
--   与新建行都有确定值（0 = 不启用），否则 SELECT/INSERT 会因 NOT NULL 无默认而在运行期 1364。
--   与 liability_deduct / liability_limit 同口径（DECIMAL(12,2) NOT NULL DEFAULT 0）。
--
-- 与 §2 原稿的关系：原稿责任配置只有 liability_deduct 一列（年度免赔）；本次属**追加列**，不改既有列语义。

ALTER TABLE t_adj_liability
  ADD COLUMN invoice_deduct DECIMAL(12,2) NOT NULL DEFAULT 0
    COMMENT '发票级免赔(GAP②)：比例前、年度免赔前，按费用池基数占比分摊扣减；0=不启用'
    AFTER liability_limit,
  ADD COLUMN adjust_deduct DECIMAL(12,2) NOT NULL DEFAULT 0
    COMMENT '理算级免赔(GAP⑥)：比例后、控额前，对理算金额二次绝对额扣减；0=不启用'
    AFTER invoice_deduct;
