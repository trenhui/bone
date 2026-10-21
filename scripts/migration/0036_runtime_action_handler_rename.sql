-- =============================================================================
-- 0036_runtime_action_handler_rename.sql
--
-- 目的（费用报销建模方案 §0.1 命名纪律 · 代码整改）：
--   报销「确认打款」逃逸处理器 bean 名由 paymentApplicationService 改名为
--   expenseReimbursementConfirmPayoutHandler（类 ExpenseReimbursementConfirmPayoutHandler）。
--   理由：paymentApplicationService 与 blueprint 订单域 PaymentApplicationService
--   同名同 bean 名——语义上报销打款 ≠ 订单支付，命名纪律被实现破坏，且将来业务
--   模块以 starter 装配进宿主时同名 bean 会 fail-fast 冲突（Spring 容器按名唯一）。
--
--   0032 种子已同步新名；本脚本负责存量环境 UPDATE（幂等，可重复执行）。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0036_runtime_action_handler_rename.sql
-- 前置：0032_runtime_action_spi.sql（动作定义表已建、confirmPayout 已种）。
-- 后置：应用重启后 bean 名生效（RuntimeActionDispatcher 按 Spring Map<beanName,bean> 解析）。
-- =============================================================================

USE bone;

UPDATE meta_entity_action_def
   SET handler_bean    = 'expenseReimbursementConfirmPayoutHandler',
       updated_at      = NOW(3),
       version         = version + 1
 WHERE handler_bean    = 'paymentApplicationService'
   AND deleted         = 0;

-- ── 校验：无残留旧 bean 名 ─────────────────────────────────────────────────
SELECT COUNT(*) AS remaining_old_bean
  FROM meta_entity_action_def
 WHERE handler_bean = 'paymentApplicationService' AND deleted = 0;
