-- ============================================================
-- claim-assign-gray-stats.sql —— AUTO_ASSIGN 灰度窗口命中率与回落计数
--
-- 一句话：按小时统计「规则命中 / 回落入池」比例，回答「自动分配到底有没有生效」。
--
-- 为什么必须按小时分桶而不是只给一个总命中率
-- ----------------------------------------------------------------
-- 开关是按时间窗逐步放开的：上午 DISABLED、下午 ENABLED 时，全天总命中率会被
-- 关闭时段稀释成一个既不代表上午也不代表下午的数——运维据此判断「灰度是否达标」
-- 会得出错误结论。按小时分桶后，关闭时段整小时无数据、开启时段逐小时可读。
--
-- 为什么排除 BACKFILL
-- ----------------------------------------------------------------
-- 存量回填（assign_reason=BACKFILL）是「照抄既有 first_reviewer_id」，不是规则求解的结果。
-- 把它算进命中率等于用回填数据冒充规则效果——命中率会虚高，而规则可能压根没配。
--
-- 为什么 ALL_INELIGIBLE 要单列
-- ----------------------------------------------------------------
-- NO_RULE = 没配规则（配置缺失，运营补规则即可）；
-- ALL_INELIGIBLE = 配了规则但目标全不可用（组织架构/角色失效，需要改数据不是改配置）。
-- 两者处置完全不同，合并成一个「回落数」会让值班同学按错方向排查。
--
-- 占位符 {{SINCE}} / {{TENANT_FILTER}} 由 scripts/ops/claim_assign_gray_report.py 注入
-- （占位而不用 :since / %()s，是为了让本文件也能被 DBA 直接粘进客户端执行——
--   pymysql 的 %()s 语法在客户端里不成立）。
--
-- {{TENANT_FILTER}} 展开为空串或 "AND tenant_id = <int>"：单租户排查时必须真下推过滤，
-- 否则「传了 --tenant 却看到全租户数字」比没有这个参数更危险。int 由 argparse 保证。
-- ============================================================

SELECT
       DATE_FORMAT(created_at, '%Y-%m-%d %H:00')                AS hour_bucket,
       COUNT(*)                                                  AS task_total,
       SUM(CASE WHEN assign_reason = 'RULE_MATCH' THEN 1 ELSE 0 END)      AS matched,
       SUM(CASE WHEN assign_reason = 'NO_RULE' THEN 1 ELSE 0 END)         AS no_rule,
       SUM(CASE WHEN assign_reason = 'ALL_INELIGIBLE' THEN 1 ELSE 0 END)  AS all_ineligible,
       ROUND(
         SUM(CASE WHEN assign_reason = 'RULE_MATCH' THEN 1 ELSE 0 END) / COUNT(*),
         4)                                                      AS hit_rate
  FROM t_claim_task
 WHERE deleted = 0
   AND created_at >= '{{SINCE}}'
   AND assign_reason IS NOT NULL
   AND assign_reason <> 'BACKFILL'
   {{TENANT_FILTER}}
 GROUP BY hour_bucket
 ORDER BY hour_bucket;
