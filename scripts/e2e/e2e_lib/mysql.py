# -*- coding: utf-8 -*-
"""第二证据源：直查 MySQL（防「接口返回 200 但没落库」的假绿）。

断言双证据口径：API 断言 + 落库断言同时为真才算绿——接口层 bug（如静默失败、
写到别的表）在 API 侧看起来一切正常，只有直查物理表能戳穿。
"""
import subprocess

from . import config


def query(sql):
    """执行一条只读 SQL，返回 `(stdout | None, stderr | None)`。

    失败（客户端缺失 / 连接失败 / SQL 错误）返回 `(None, 错误摘要)`，由断言判红，
    不抛栈——一个查询失败不该毁掉整轮覆盖。
    """
    try:
        r = subprocess.run(
            [
                config.MYSQL,
                f"-h{config.MYSQL_HOST}",
                f"-P{config.MYSQL_PORT}",
                f"-u{config.MYSQL_USER}",
                f"-p{config.MYSQL_PWD}",
                config.MYSQL_DB,
                "-e",
                sql,
            ],
            capture_output=True,
            text=True,
            timeout=30,
        )
    except FileNotFoundError:
        return None, f"mysql 客户端不存在: {config.MYSQL}"
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"
    if r.returncode != 0:
        return None, (r.stderr or "").strip()[:200]
    return (r.stdout or "").strip(), None
