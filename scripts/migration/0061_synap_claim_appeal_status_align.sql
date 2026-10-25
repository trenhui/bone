-- 0061 申诉表终态列口径对齐：数字码 → ClaimStatus 枚举名
--
-- 背景：t_claim_case.status 落的是枚举 name（ClaimCase#transition 写 to.name()），
--       而 appeal_record.original_status 落的是 pk-tpa 数字码（45/50）——同一语义两种编码。
--       这类双轨不会报错，只会静默错配：按数字码写的过滤条件一行都命中不到，
--       跨表核对（案件终态 vs 申诉原终态）也永远对不上。
--
-- 处置：统一为枚举名。VARCHAR(4) 装不下 'CLOSED_REJECTED'，故先扩宽再回填，顺序不可颠倒。
-- 幂等：重复执行时 MODIFY 无实质变化，UPDATE 的 WHERE 也已无匹配行。

ALTER TABLE appeal_record
  MODIFY original_status VARCHAR(32) NOT NULL
  COMMENT '被申诉原终态: ClaimStatus 枚举名 CLOSED_REJECTED/FINISHED（与 t_claim_case.status 同口径）';

UPDATE appeal_record
   SET original_status = CASE original_status
                           WHEN '45' THEN 'CLOSED_REJECTED'
                           WHEN '50' THEN 'FINISHED'
                           ELSE original_status
                         END
 WHERE original_status IN ('45', '50');
