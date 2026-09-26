#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bone-system 字典种子数据初始化（两级模型，幂等，可重复运行）

联调发现字典表为空 → 前端字典管理页左栏无类型可选。本脚本通过 API 注入一组贴合系统管理场景的
通用字典（含一个 CASCADE 级联值域用于验证树形能力）；已存在的类型/项自动跳过，不会重复插入。

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

# 种子值域：(code, name, category, maxDepth, codeSegments, [(code, label, value, sort, parent_code)])
SEED = [
    ("sys_status", "系统状态", "LIST", 0, None, [
        ("ENABLED", "启用", "1", 1, None),
        ("DISABLED", "禁用", "0", 2, None),
    ]),
    ("sys_yes_no", "是否", "LIST", 0, None, [
        ("YES", "是", "1", 1, None),
        ("NO", "否", "0", 2, None),
    ]),
    ("sys_log_level", "日志级别", "LIST", 0, None, [
        ("DEBUG", "DEBUG", "DEBUG", 1, None),
        ("INFO", "INFO", "INFO", 2, None),
        ("WARN", "WARN", "WARN", 3, None),
        ("ERROR", "ERROR", "ERROR", 4, None),
    ]),
    ("sys_alert_level", "告警级别", "LIST", 0, None, [
        ("INFO", "提示", "info", 1, None),
        ("WARNING", "警告", "warning", 2, None),
        ("CRITICAL", "严重", "critical", 3, None),
    ]),
    # 级联样例：省 → 市（GB/T 2260 六位编码，2,2,2 分段可自动推导父级）
    ("biz_region", "行政区域", "CASCADE", 3, "2,2,2", [
        ("GD", "广东省", "440000", 1, None),
        ("GZ", "广州市", "440100", 1, "GD"),
        ("SZ", "深圳市", "440300", 2, "GD"),
        ("ZJ", "浙江省", "330000", 2, None),
        ("HZ", "杭州市", "330100", 1, "ZJ"),
        ("NB", "宁波市", "330200", 2, "ZJ"),
    ]),
]

TAG_BY_TYPE = {"sys_status": {"ENABLED": "success", "DISABLED": "default"},
               "sys_alert_level": {"INFO": "info", "WARNING": "warning", "CRITICAL": "error"}}


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
    print(f"已登录 admin，开始注入字典种子（共 {len(SEED)} 个值域 / "
          f"{sum(len(i[4]) for i in SEED)} 项）...")

    created_items = 0
    skipped = 0
    for code, name, category, max_depth, code_segments, items in SEED:
        st, d, _ = http("GET", SYS, f"/system/dict/types/{code}", token=token)
        if not (d and d.get("data")):
            body = {"code": code, "name": name, "category": category, "moduleCode": "system",
                    "maxDepth": max_depth, "codeSegments": code_segments,
                    "description": "种子数据（seed-system-dicts.py）",
                    "sort": 0, "status": 1, "builtin": False}
            st, _, _ = http("POST", SYS, "/system/dict/types", body=body, token=token)
            print(f"  [{'OK' if st == 200 else 'FAIL'}] 值域 {code}（{category}）创建 status={st}")

        # 已存在的项编码：级联值域用树接口，其余用分页接口
        if category == "CASCADE":
            st, d, _ = http("GET", SYS, "/system/dict/items/tree",
                            params={"typeCode": code}, token=token)
            nodes = d.get("data") or [] if d else []
            existing = set()
            def walk(ns):
                for n in ns:
                    existing.add(n.get("code"))
                    walk(n.get("children") or [])
            walk(nodes)
        else:
            st, d, _ = http("GET", SYS, "/system/dict/items/page",
                            params={"typeCode": code, "pageSize": 500}, token=token)
            existing = {x.get("code") for x in ((d or {}).get("data", {}) or {}).get("list", [])}

        for item_code, label, value, sort, parent in items:
            if item_code in existing:
                skipped += 1
                continue
            body = {"typeCode": code, "parentCode": parent, "code": item_code,
                    "label": label, "value": value, "sort": sort, "status": 1,
                    "tagType": TAG_BY_TYPE.get(code, {}).get(item_code, "default")}
            st, _, _ = http("POST", SYS, "/system/dict/items", body=body, token=token)
            if st == 200:
                created_items += 1
                print(f"  [+] {code}/{item_code} ({label})")
            else:
                print(f"  [FAIL] {code}/{item_code} status={st}")
    print(f"字典种子注入完成: 新增项 {created_items} 条, 已存在跳过 {skipped} 条")

    # 验证：类型分页非空 + 级联值域能读出树
    st, d, _ = http("GET", SYS, "/system/dict/types/page",
                    params={"pageNum": 1, "pageSize": 100}, token=token)
    total = (d.get("data", {}).get("total") if d and d.get("data") else None)
    ok_types = isinstance(total, int) and total > 0

    st2, t, _ = http("GET", SYS, "/system/dict/items/tree",
                     params={"typeCode": "biz_region"}, token=token)
    tree = (t.get("data") or []) if t else []
    ok_tree = any(n.get("children") for n in tree)

    print(f"验证: 值域总数={total} -> {'PASS' if ok_types else 'FAIL'}; "
          f"biz_region 树层级={len(tree)} 个根节点 -> {'PASS' if ok_tree else 'FAIL'}")
    return 0 if (ok_types and ok_tree) else 2


if __name__ == "__main__":
    sys.exit(main())
