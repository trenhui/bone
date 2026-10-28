"""UAT 灌数脚本的数据库连接真源（口令只从环境变量取，禁止字面量）。

为什么不写默认口令：`gitleaks` 门禁（本地 pre-commit + CI `backend-security` job）
会把 `password="…"` 形式的字面量判为硬编码凭据并**阻断提交**；本地开发口令
同样属于凭据，写进仓库即等于泄漏给所有克隆者。因此本目录统一走环境变量
`BONE_UAT_DB_PASSWORD`（不落盘、不进 shell 历史）：

    read -rs BONE_UAT_DB_PASSWORD && export BONE_UAT_DB_PASSWORD

缺失时 **fail-fast** 并给出提示，而不是静默回退到某个"方便"的默认口令——
静默回退会让脚本在错误的环境上跑清理，风险远大于多敲一条 read。

其余连接参数（host/port/user/database）可用 `BONE_UAT_DB_*` 覆盖，默认对齐
本仓库 `docker-compose` / 本地 MySQL 口径。
"""

import os

HOST = os.environ.get("BONE_UAT_DB_HOST", "127.0.0.1")
PORT = int(os.environ.get("BONE_UAT_DB_PORT", "3306"))
USER = os.environ.get("BONE_UAT_DB_USER", "root")
DATABASE = os.environ.get("BONE_UAT_DB_NAME", "bone")

# 不在模块导入期校验：`--help` / `--schema-only` 这类不需要连库的路径不应被打断。
_PASSWORD = os.environ.get("BONE_UAT_DB_PASSWORD")


def conf():
    """返回 `pymysql.connect` 的关键字参数；缺口令时 fail-fast。"""
    if not _PASSWORD:
        raise SystemExit(
            "缺少环境变量 BONE_UAT_DB_PASSWORD。\n"
            "  UAT 脚本禁止硬编码数据库口令（gitleaks 门禁会阻断提交），请先：\n"
            "    read -rs BONE_UAT_DB_PASSWORD && export BONE_UAT_DB_PASSWORD\n"
            "  其余参数可用 BONE_UAT_DB_HOST / BONE_UAT_DB_PORT / BONE_UAT_DB_USER / "
            "BONE_UAT_DB_NAME 覆盖。"
        )
    return dict(host=HOST, port=PORT, user=USER, password=_PASSWORD, database=DATABASE)


def connect(**overrides):
    """建连：`connect()` 或 `connect(autocommit=False)`。"""
    import pymysql

    kwargs = conf()
    kwargs.update(overrides)
    return pymysql.connect(**kwargs)
