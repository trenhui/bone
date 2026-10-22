#!/usr/bin/env python3
"""
0052 改名迁移 · CI / 本地可重复校验运行器

把「改名前旧码」植入最小镜像库 → 执行 0052 主脚本 → 跑 0052_verify.sql 自校验
→ 断言 7 项指标达标。全程在临时库进行，结束清理，幂等可重复。

用法：
    python3 scripts/migration/verify_0052.py
连接来源（优先级）：环境变量 MYSQL_*  >  仓库 .env 的 BONE_DB_*  >  本地默认值
    MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD
"""
import os
import sys
import time
import pymysql

HERE = os.path.dirname(os.path.abspath(__file__))
MIG = os.path.join(HERE, '0052_rename_expense_claim_to_reimbursement.sql')
VER = os.path.join(HERE, '0052_verify.sql')

HOST = os.getenv('MYSQL_HOST')
PORT = os.getenv('MYSQL_PORT')
USER = os.getenv('MYSQL_USER')
PASS = os.getenv('MYSQL_PASSWORD')

# 本地兜底：从仓库 .env 读取 BONE_DB_*（CI 中由环境变量直接提供，不会走到这里）
_env_path = os.path.join(HERE, '..', '..', '.env')
if os.path.exists(_env_path):
    for line in open(_env_path, encoding='utf-8'):
        line = line.strip()
        if '=' not in line or line.startswith('#'):
            continue
        k, v = line.split('=', 1)
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k == 'BONE_DB_HOST' and not HOST:
            HOST = v
        elif k == 'BONE_DB_PORT' and not PORT:
            PORT = int(v)
        elif k == 'BONE_DB_USERNAME' and not USER:
            USER = v
        elif k == 'BONE_DB_PASSWORD' and not PASS:
            PASS = v

HOST = HOST or '127.0.0.1'
PORT = int(PORT or 3306)
USER = USER or 'root'
PASS = PASS or 'bone_ci_only'


def log(*a):
    print(*a, flush=True)


def load_stmts(path):
    lines = [l for l in open(path, encoding='utf-8')
             if l.strip() != 'USE bone;' and not l.strip().startswith('--')]
    return [s.strip() for s in '\n'.join(lines).split(';') if s.strip()]


def main():
    scr = 'bone_0052_ci_%d' % int(time.time())
    root = pymysql.connect(host=HOST, port=PORT, user=USER, password=PASS,
                           charset='utf8mb4', connect_timeout=15)
    try:
        rc = root.cursor()
        rc.execute('SET SESSION lock_wait_timeout=10')
        rc.execute('DROP DATABASE IF EXISTS %s' % scr)
        rc.execute('CREATE DATABASE %s CHARSET utf8mb4' % scr)
        log('created temp db %s' % scr)
        conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=PASS,
                               database=scr, charset='utf8mb4', connect_timeout=15)
        cur = conn.cursor()
        # 0052 触碰的最小表结构
        cur.execute('CREATE TABLE meta_entity(id BIGINT, code VARCHAR(255), table_name VARCHAR(255), deleted TINYINT)')
        cur.execute('CREATE TABLE meta_entity_action_def(id BIGINT, entity_code VARCHAR(255), handler_bean VARCHAR(255), deleted TINYINT)')
        cur.execute('CREATE TABLE iam_permission(id BIGINT, code VARCHAR(255), resource_path VARCHAR(255), deleted TINYINT)')
        cur.execute('CREATE TABLE meta_entity_ui_schema(id BIGINT, schema_json LONGTEXT, published_schema_json LONGTEXT)')
        cur.execute('CREATE TABLE meta_field(id BIGINT, validation_rules LONGTEXT)')
        cur.execute('CREATE TABLE meta_model_template(id BIGINT, ui_schema_json LONGTEXT)')
        cur.execute('CREATE TABLE t_expense_claim(id BIGINT)')
        cur.execute('CREATE TABLE t_expense_claim_item(id BIGINT)')
        # 改名前旧码种子（主/子实体、action_def×4、iam 基名+子实体×6、JSON 内嵌旧码）
        cur.execute("INSERT INTO meta_entity VALUES "
                    "(1,'expense_claim','t_expense_claim',0),"
                    "(2,'expense_claim_item','t_expense_claim_item',0),"
                    "(3,'other_entity','t_other',0)")
        cur.execute("INSERT INTO meta_entity_action_def VALUES "
                    "(1,'expense_claim','expenseClaimConfirmPayoutHandler',0),"
                    "(2,'expense_claim','someBean',0),"
                    "(3,'expense_claim','anotherBean',0),"
                    "(4,'expense_claim','xBean',0)")
        cur.execute("INSERT INTO iam_permission VALUES "
                    "(1,'runtime:expense_claim:read','/api/v1/runtime/entities/expense_claim/**',0),"
                    "(2,'runtime:expense_claim:write','/api/v1/runtime/entities/expense_claim/**',0),"
                    "(3,'runtime:expense_claim:delete','/api/v1/runtime/entities/expense_claim/**',0),"
                    "(4,'runtime:expense_claim_item:read','/api/v1/runtime/entities/expense_claim_item/**',0),"
                    "(5,'runtime:expense_claim_item:write','/api/v1/runtime/entities/expense_claim_item/**',0),"
                    "(6,'runtime:expense_claim_item:delete','/api/v1/runtime/entities/expense_claim_item/**',0)")
        cur.execute("INSERT INTO meta_entity_ui_schema VALUES "
                    "(1,'{\"entityCode\":\"expense_claim_item\"}','{\"entityCode\":\"expense_claim_item\"}')")
        cur.execute("INSERT INTO meta_field VALUES (1,'{\"childEntityCode\":\"expense_claim_item\"}')")
        cur.execute("INSERT INTO meta_model_template VALUES (1,'{\"entityCode\":\"expense_claim\"}')")
        conn.commit()
        log('seeded pre-state (old codes)')

        for st in load_stmts(MIG):
            cur.execute(st)
        conn.commit()
        log('applied 0052')

        last = None
        for st in load_stmts(VER):
            cur.execute(st)
            if st.upper().startswith('SELECT'):
                last = cur.fetchall()
        cur.close()
        conn.close()

        cols = ['new_tables', 'leftover_old_tables', 'renamed_entities_active',
                'renamed_actions', 'renamed_perms', 'leftover_old_perms', 'leftover_old_code']
        vals = dict(zip(cols, last[0]))
        log('self-check: %s' % vals)
        expect = {'new_tables': 2, 'leftover_old_tables': 0, 'renamed_entities_active': 1,
                  'renamed_actions': 1, 'renamed_perms': 6, 'leftover_old_perms': 0, 'leftover_old_code': 0}
        ok = True
        for k, v in expect.items():
            if vals[k] < v:
                ok = False
                log('  FAIL %s=%s (expect >= %s)' % (k, vals[k], v))
        if ok:
            log('PASS: 0052 migration verified (renamed_perms=6, leftover=0)')
            return 0
        log('FAIL: 0052 migration self-check not met')
        return 1
    finally:
        try:
            rc.execute('DROP DATABASE IF EXISTS %s' % scr)
            log('dropped temp db %s' % scr)
        except Exception as e:  # noqa: BLE001
            log('cleanup warn: %s' % str(e)[:80])
        root.close()


if __name__ == '__main__':
    sys.exit(main() or 0)
