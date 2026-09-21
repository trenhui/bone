#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bone-system 字典种子数据初始化（幂等，可重复运行）

联调发现 sys_dict 表为空 → 前端 DictManagement 的「类型」下拉为空，无任何初始数据。
本脚本通过 API（admin token 自动带租户）注入一组贴合系统管理场景的通用字典，
让字典管理页开箱即有可用数据；已存在的 (type, code) 自动跳过，不会重复插入。

用法:
  python3 scripts/seed-system-dicts.py
依赖: Python 标准库；需 bone-system(8083) 与 bone-iam(8081) 在运行。
"""
import sys
import json
import urllib.request
import urllib.error
import urllib.parse

SYS = "http://localhost:8083/api/v1"
IAM = "http://localhost:8081/api/v1"

# 种子字典：(type, typeName, [(code, label, value, sort)])
SEED = [
    ("SYS_STATUS", "系统状态", [
        ("ENABLED", "启用", "1", 1),
        ("DISABLED", "禁用", "0", 2),
    ]),
    ("SYS_YES_NO", "是否", [
        ("YES", "是", "1", 1),
        ("NO", "否", "0", 2),
    ]),
    ("SYS_LOG_LEVEL", "日志级别", [
        ("DEBUG", "DEBUG", "DEBUG", 1),
        ("INFO", "INFO", "INFO", 2),
        ("WARN", "WARN", "WARN", 3),
        ("ERROR", "ERROR", "ERROR", 4),
    ]),
    ("SYS_ALERT_LEVEL", "告警级别", [
        ("INFO", "提示", "info", 1),
        ("WARNING", "警告", "warning", 2),
        ("CRITICAL", "严重", "critical", 3),
    ]),
]


def http(method, base, path, params=None, body=None, token=None, timeout=25):
    url = base + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8")
            status = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        status = e.code
    except Exception as e:  # noqa: BLE001
        return None, None, str(e)
    try:
        parsed = json.loads(raw) if raw else None
    except Exception:  # noqa: BLE001
        parsed = raw
    return status, parsed, None


def main():
    st, login, _ = http("POST", IAM, "/iam/login", body={"username": "admin", "password": "123456"})
    token = (login.get("data", {}).get("token") if login else None)
    if not token:
        print(f"[FAIL] 登录 IAM 失败 status={st}")
        return 1
    print(f"已登录 admin，开始注入字典种子（共 {sum(len(i[2]) for i in SEED)} 条）...")

    created = 0
    skipped = 0
    for dtype, type_name, items in SEED:
        st, d, _ = http("GET", SYS, f"/system/dicts/type/{dtype}", token=token)
        existing = set()
        if d and isinstance(d.get("data"), list):
            existing = {x.get("code") for x in d["data"]}
        for code, label, value, sort in items:
            if code in existing:
                skipped += 1
                continue
            body = {"type": dtype, "typeName": type_name, "code": code,
                    "label": label, "value": value, "sort": sort, "status": 1}
            st, _, _ = http("POST", SYS, "/system/dicts", body=body, token=token)
            if st == 200:
                created += 1
                print(f"  [+] {dtype}/{code} ({label})")
            else:
                print(f"  [FAIL] {dtype}/{code} status={st}")
    print(f"字典种子注入完成: 新增 {created} 条, 已存在跳过 {skipped} 条")

    # 验证：注入后字典列表非空
    st, d, _ = http("GET", SYS, "/system/dicts/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    total = (d.get("data", {}).get("total") if d and d.get("data") else None)
    types = sorted({x.get("type") for x in (d.get("data", {}).get("list") or [])}) if d and d.get("data") else []
    ok = isinstance(total, int) and total > 0
    print(f"验证: 字典总数={total}, 类型={types} -> {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
