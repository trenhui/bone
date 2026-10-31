#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""D3 安全守卫：iam_account 不得成为运行时实体（REFERENCE 目标）。

为什么需要（见 ADR-0044 决策 3）：
  approver_id 的元数据标注 type=REFERENCE → iam_account，但 iam_account 当前**未**注册进
  meta_entity。一旦有人把它注册成运行时实体（想「完成」REFERENCE 接线），lookupOptions 会
  走 RuntimeRelationSupport 的 `SELECT * FROM`，把 password_hash 等凭据列原样返回给浏览器
  —— 这是安全红线。在接线完成前，必须有人工安全评审 + 列级投影改造（只返回安全列）。

本脚本是**回归守卫**：现在 iam_account 没注册 ⇒ 退出 0；将来若被注册 ⇒ 退出 1，
CI / 提交门禁据此拦下「裸接 REFERENCE」的 PR，强制先做安全改造。

只读，不写任何表。环境变量同 seed-bootstrap.py：
BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME。
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path


def find_client() -> str | None:
    for cand in (
        os.environ.get("MYSQL_CLIENT"),
        shutil.which("mysql"),
        "/usr/local/mysql/bin/mysql",
        "/opt/homebrew/bin/mysql",
        "/usr/bin/mysql",
    ):
        if cand and Path(cand).exists():
            return cand
    return None


def main() -> int:
    cli = find_client()
    if not cli:
        print("⚠️ 找不到 mysql 客户端，跳过 iam_account 暴露守卫（设 MYSQL_CLIENT 或装到 PATH）")
        return 0
    host = os.environ.get("BONE_DB_HOST", "127.0.0.1")
    port = os.environ.get("BONE_DB_PORT", "3306")
    user = os.environ.get("BONE_DB_USER", "root")
    pw = os.environ.get("BONE_DB_PASSWORD", "mysql123")
    name = os.environ.get("BONE_DB_NAME", "bone")

    sql = "SELECT code FROM meta_entity WHERE deleted = 0"
    out = subprocess.run(
        [cli, f"-h{host}", f"-P{port}", f"-u{user}", f"-p{pw}", "-B", name, "-e", sql],
        capture_output=True,
        text=True,
    )
    if out.returncode != 0:
        tail = (out.stderr.strip().splitlines() or ["(无输出)"])[-1]
        # meta_entity 不存在 ≠ 暴露，放行但提示（避免无表环境误判红）
        if "doesn't exist" in tail or "Unknown table" in tail:
            print("⚠️ meta_entity 表不存在，无法判定 iam_account 注册态（放行）")
            return 0
        print(f"❌ 查询失败：{tail}")
        return 1

    registered = {r.strip() for r in out.stdout.splitlines() if r.strip()}
    if "iam_account" in registered:
        print("❌ 安全红线：iam_account 已被注册为运行时实体（meta_entity）。")
        print("   approver_id 的 REFERENCE→iam_account 一旦接线，lookupOptions 的 SELECT * 会把")
        print("   password_hash 等凭据列返回给浏览器。在接线前必须：")
        print("   ① 做列级投影改造（只返回 id/name 等安全列，禁止 SELECT *）；")
        print("   ② 通过独立安全评审（ADR-0044 决策 3 的退出门禁）。")
        return 1

    print(
        "✅ 守卫通过：iam_account 未注册为运行时实体，REFERENCE→iam_account 仍处于暂缓态"
        "（ADR-0044 决策 3）。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
