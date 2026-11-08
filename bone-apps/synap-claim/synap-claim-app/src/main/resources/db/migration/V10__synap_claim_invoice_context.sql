-- synap-claim 第十批 DDL 变更：「次」分组维度落地（V1 §7.3）
--
-- 背景：控额栈的次限额（层③）需要先按「次」的定义分组，而六档定义里有五档依赖
--      发票日期 / 医院 / 科室 / 病种。这三类维度此前在库里**根本不存在**，于是
--        ① 配了"同日/同院/同科室"的责任在运行期一律被拒绝（转人工率 100%）；
--        ② 若为了"跑得通"而静默并成一组，两天各 500、次限额 300 的案子会赔 300 而不是 600（少赔）。
--      两者都不可接受，故补齐维度列，并把"缺失时能否降级"的判断交给引擎用可证的条件决定。
--
-- 为什么把发票日期/医院/病种放在 t_claim_case（发票级）而不是每条明细：
--      本模型的赔案与发票是 1:1（t_claim_case 上 uk_tenant_invoice 唯一键限定单一发票号），
--      一次就诊一个开票日期、一家医院、一个主要诊断。放到明细上会引入"同一案内出现多个开票日期"
--      这种 schema 允许但业务上非法的一致性裂缝，且每条明细重复存同一个值。
--
-- 为什么科室放在 t_claim_item（明细级）：
--      一张发票可跨科室（同日多科就诊合并结算），科室是**行级**事实；这也正是"同院同科室"
--      这一档能真正把一次就诊拆成多「次」的唯一维度。
--
-- 历史数据：三列用 NULL / 空串缺省，不回填、不猜测。引擎对缺失的处理见
--      OccurrenceGrouper#requireDimensions（可证单票 ⇒ 降级并留痕；不可证 ⇒ 拒绝）。

ALTER TABLE t_claim_case
  ADD COLUMN invoice_date DATE DEFAULT NULL
    COMMENT '发票开票日期(「次」分组维度；历史案件为 NULL，不得用出险时间顶替)',
  ADD COLUMN hospital_code VARCHAR(64) NOT NULL DEFAULT ''
    COMMENT '医院编码(「次」分组维度；按编码比对，不按名称——名称可改，编码是身份)',
  ADD COLUMN diagnosis_code VARCHAR(64) NOT NULL DEFAULT ''
    COMMENT '病种/诊断编码(「次」分组维度；一次就诊一个主要诊断)';

ALTER TABLE t_claim_item
  ADD COLUMN department VARCHAR(128) NOT NULL DEFAULT ''
    COMMENT '就诊科室(行级「次」分组维度；一张发票可跨科室，故必须落在明细上)';
