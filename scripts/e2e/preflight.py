#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""E2E 失败快检（preflight）—— 跑任何 E2E 之前先花 2 秒确认环境可用。

**为什么需要**：此前栈没起时，各脚本各自等 `BONE_E2E_TIMEOUT`（默认 40s）超时，
11 个 Python 全链路 + 20 个 UI 探针逐个失败，日志里满是 `Connection refused`，
看日志的人要自己推断「是代码坏了还是服务没起」。preflight 把这件事变成一条明确结论：
「8081 不可达，先起服务」，且**退出码非零**，CI 能第一时间停在这里而不是跑满半小时。

**端点全部从 `e2e_lib.config` 派生**（12-Factor）：切环境只改 `BONE_E2E_*`，
本脚本零硬编码端口。

用法：
    python3 scripts/e2e/preflight.py          # 核心：API 网关/登录端点 + MySQL
    python3 scripts/e2e/preflight.py --ui     # 追加前端壳层（UI 探针必需）
    python3 scripts/e2e/preflight.py --deep   # 追加真实登录探活（校验凭据，不只是端口）
    python3 scripts/e2e/preflight.py --all    # = --ui --deep

退出码：0 全通；1 有端点不可达（或 --deep 登录失败）。
"""
import argparse
import socket
import sys
import time
from urllib.parse import urlparse

from e2e_lib import config

# 「怎么起」提示：先按**标签**匹配（换端口也照样给对上），再按端口兜底
_HINTS_BY_LABEL = {
    "API 网关": "起 API 网关 gateway（java -jar，默认 :8888）",
    "登录端点": "登录端点默认与网关同址；直连后端时指到承载 /iam/login 的 IAM（:8081）",
    "前端壳层": "起前端壳层 shell（默认 :3000，qiankun 宿主；子应用 3003-3012 随其后端）",
    "MySQL 直查": "起 MySQL（或用 BONE_E2E_MYSQL_HOST/PORT 指向其它实例）",
}
_HINTS_BY_PORT = {
    8081: "./scripts/local-up.sh（起 iam/system/masterdata/file/integration）",
    8082: "起 blueprint（:8082，java -jar）",
    8084: "起 masterdata（:8084）",
    9001: "起 metadata-server（:9001，java -jar）",
}


def _hint_for(labels, port):
    for label in labels:
        if label in _HINTS_BY_LABEL:
            return _HINTS_BY_LABEL[label]
    return _HINTS_BY_PORT.get(port, "起对应服务，或改 BONE_E2E_* 指向正确地址")


def _hostport(url: str):
    """从 URL 取 (host, port)，缺端口按协议补默认值。"""
    u = urlparse(url)
    host = u.hostname or "127.0.0.1"
    port = u.port or (443 if u.scheme == "https" else 80)
    return host, port


def _targets(include_ui: bool):
    """收集去重后的检查目标：{ (host, port): [标签...] }。

    BASE 与 LOGIN_BASE 默认同址（都打网关），去重后只探一次，但标签都保留，
    这样失败提示能同时说清「API 与登录都挂在它上面」。
    """
    targets = {}

    def add(label, host, port):
        targets.setdefault((host, port), []).append(label)

    add("API 网关", *_hostport(config.BASE))
    add("登录端点", *_hostport(config.LOGIN_BASE))
    if include_ui:
        add("前端壳层", *_hostport(config.UI_BASE))
    add("MySQL 直查", config.MYSQL_HOST, int(config.MYSQL_PORT))
    return targets


def _probe(host: str, port: int, timeout: float):
    """TCP 连通性探测，返回 (可达?, 耗时ms, 失败原因)。"""
    t0 = time.time()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True, int((time.time() - t0) * 1000), ""
    except OSError as exc:
        return False, int((time.time() - t0) * 1000), exc.strerror or str(exc)


def main(argv=None):
    ap = argparse.ArgumentParser(description="E2E 失败快检（环境连通性）")
    ap.add_argument("--ui", action="store_true", help="追加前端壳层检查（UI 探针必需）")
    ap.add_argument("--deep", action="store_true", help="追加真实登录探活（校验凭据）")
    ap.add_argument("--all", action="store_true", help="等价于 --ui --deep")
    ap.add_argument("--timeout", type=float, default=2.0, help="单端点连接超时秒数（默认 2）")
    args = ap.parse_args(argv)

    include_ui = args.ui or args.all
    deep = args.deep or args.all

    print("\n" + "=" * 78)
    print("E2E 失败快检（preflight）")
    print("=" * 78)

    targets = _targets(include_ui)
    failures = []
    for (host, port), labels in targets.items():
        ok, ms, why = _probe(host, port, args.timeout)
        mark = "✓" if ok else "✗"
        cost = f"{ms}ms" if ok else f"{ms}ms {why}"
        print(f"  {mark} {'/'.join(labels):<28} {host}:{port:<6} {cost}")
        if not ok:
            hint = _hint_for(labels, port)
            failures.append(f"{'/'.join(labels)}（{host}:{port}）：{hint}")

    # --deep：端口通不代表能用，真登一次才证明「凭据 + 登录路由 + JWT 签发」都活着
    if deep and not failures:
        from e2e_lib.http import Session  # 延迟导入：端口不通时不必加载 HTTP 层

        try:
            tok = Session().login(fatal=False)
        except Exception as exc:  # noqa: BLE001 —— 任何异常都算登录失败，需如实报出
            tok, err = None, str(exc)
        else:
            err = "" if tok else "登录返回非 200 或无 token"
        if tok:
            print(f"  ✓ {'登录探活 ' + config.USER:<28} {config.LOGIN_BASE} token={tok[:12]}…")
        else:
            print(f"  ✗ {'登录探活 ' + config.USER:<28} {config.LOGIN_BASE} {err}")
            failures.append(
                f"登录 {config.USER}@{config.LOGIN_BASE}：{err}"
                "（核对 BONE_E2E_USER/PWD；或 BONE_E2E_LOGIN_BASE 是否指到承载 /iam/login 的服务）"
            )

    if failures:
        print("\n✗ 环境不可用 —— 先修好再跑 E2E（否则每个脚本各自等超时，日志难判读）")
        for f in failures:
            print(f"  · {f}")
        return 1

    print(f"\n✓ {len(targets)} 个端点全部可达{'，登录探活通过' if deep else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
