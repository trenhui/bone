# -*- coding: utf-8 -*-
"""E2E 运行环境配置（唯一真源）。

所有可调项一律走环境变量，**不改脚本即可切换环境**：

| 变量 | 默认值 | 含义 |
| --- | --- | --- |
| `BONE_E2E_BASE` | `http://127.0.0.1:8888/api/v1` | API 基址（走网关=真联调；直连后端排查时改它） |
| `BONE_E2E_USER` / `BONE_E2E_PWD` | `admin` / `123456` | 主会话凭据 |
| `BONE_E2E_TENANT` | `0` | 租户头 `X-Tenant-Id` |
| `BONE_E2E_KEEP_RESIDUE` | 关 | `1` = 跑完保留残留现场（供 UI 探针/排查） |
| `BONE_E2E_MYSQL` | 本机 8.0.31 客户端 | 第二证据源（直查落库）用的 mysql 可执行文件 |
"""
import os
import time

# 仓库根：e2e_lib 位于 <repo>/scripts/e2e/e2e_lib ⇒ 上溯三级
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))

# ---- 网络与凭据 ----
BASE = os.environ.get("BONE_E2E_BASE", "http://127.0.0.1:8888/api/v1")
# 登录端点单独可配：直连 metadata（:9001）时该端口不承载 /api/v1/iam/**（404），
# 须把登录打到 IAM（:8081）或网关；JWT 各服务同源，token 可跨端口用。
LOGIN_BASE = os.environ.get("BONE_E2E_LOGIN_BASE", BASE)
USER = os.environ.get("BONE_E2E_USER", "admin")
PWD = os.environ.get("BONE_E2E_PWD", "123456")
TENANT = os.environ.get("BONE_E2E_TENANT", "0")
TIMEOUT = int(os.environ.get("BONE_E2E_TIMEOUT", "40"))

# ---- 样例协议（建模侧界面协议的样例真源）----
SAMPLES = os.environ.get(
    "BONE_E2E_SAMPLES",
    os.path.join(REPO_ROOT, "doc", "design", "lowcode", "cases", "ui-schema-samples"),
)

# ---- 第二证据源：MySQL 直查（防「接口 200 但没落库」的假绿）----
MYSQL = os.environ.get(
    "BONE_E2E_MYSQL", "/usr/local/mysql-8.0.31-macos12-x86_64/bin/mysql"
)
MYSQL_HOST = os.environ.get("BONE_E2E_MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = os.environ.get("BONE_E2E_MYSQL_PORT", "3306")
MYSQL_USER = os.environ.get("BONE_E2E_MYSQL_USER", "root")
MYSQL_PWD = os.environ.get("BONE_E2E_MYSQL_PWD", "mysql123")
MYSQL_DB = os.environ.get("BONE_E2E_MYSQL_DB", "bone")

# ---- 残留策略 ----
KEEP_RESIDUE = os.environ.get("BONE_E2E_KEEP_RESIDUE") == "1"
CLEANER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cleanup-residue.py")

# ---- 状态文件（供 UI 探针消费的运行态交接件）----
STATE_PATH = os.environ.get("BONE_E2E_STATE", "/tmp/bone_e2e_fullchain_state.json")


def run_suffix() -> str:
    """本次运行的唯一后缀：Unix 时间戳末 9 位。

    所有**运行级**产物（实体编码/物理表/应用/模块）都带它，cleanup 按后缀定点回收。
    9 位 ≈ 3 年内不回绕，且满足 cleanup-residue.py 的 `^\\d{6,}$` 校验。
    """
    return str(int(time.time()))[-9:]
