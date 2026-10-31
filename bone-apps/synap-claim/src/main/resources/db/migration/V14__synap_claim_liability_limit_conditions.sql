-- synap-claim 第十四批 DDL 变更：适用限定（V1 §2 要素 6）落到医院 / 病种两维
--
-- 背景：V1 的适用限定是「区域 / 医院 / 医保 / 药品 / 诊疗 / 病种 × 白名单 / 黑名单」，现状是
--       ① t_adj_liability **没有任何对应列** ⇒ 业务上"限某医院"这类责任根本表达不出来；
--       ② 引擎里 LimitedConditions 恒为 disabled()（LiabilityRule.fromLegacy 从未赋过值），
--          唯一的分支是"一旦启用就整条拒绝"，即配了不可用。
--       合起来是典型的「配了不生效」：条款说有约定，系统照全额赔。
--
-- 为什么先只落医院 / 病种两维：
--      这两维的事实在 V10 已随「次」分组落到 t_claim_case（hospital_code / diagnosis_code），
--      案情自带编码，比对是**确定行为**，不需要任何外部主数据。
--      区域 / 医保 / 药品 / 诊疗四维依赖外部目录或保单层面的区域划分口径，本仓库无法闭环；
--      它们**故意不建列**——表达不出来，就不会出现"配了但引擎不读"的第三种状态。
--      将来接入时须同时补列 + 在 Engine 里补维度拒绝码，不允许只补列。
--
-- 为什么名单用分隔串而不是关联表：
--      名单是"责任配置的一部分"（随责任版本一起冻结、一起生效、一起不可变），不是可独立维护的主数据。
--      立关联表会让名单可以在责任版本冻结后被单独改动，破坏"已生效版本不可改"（§4.1）。
--      分隔串的长度上限按最坏情况估：一家三甲医院编码 8~20 字符，50 家 ≈ 1000 字符。
--
-- 为什么不加 setter：责任配置版本化后已生效版本不可改，变更走「新增版本」，与本表既有纪律一致。

ALTER TABLE t_adj_liability
  ADD COLUMN limit_mode VARCHAR(16) NOT NULL DEFAULT 'NONE'
    COMMENT '适用限定方式(V1 §2 要素6)：NONE=不启用 / WHITELIST=只赔名单内 / BLACKLIST=名单内不赔'
    AFTER daily_limit_basis,
  ADD COLUMN limit_hospital_codes VARCHAR(1024) NOT NULL DEFAULT ''
    COMMENT '适用限定-医院编码名单(逗号/顿号/分号/空白分隔，空=该维度不限定)：按编码比对，不按名称'
    AFTER limit_mode,
  ADD COLUMN limit_diagnosis_codes VARCHAR(512) NOT NULL DEFAULT ''
    COMMENT '适用限定-病种编码名单(同上分隔规则，空=该维度不限定)'
    AFTER limit_hospital_codes;
