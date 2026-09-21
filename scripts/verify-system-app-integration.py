#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bone-system ↔ bone-system-app 前后端自动联调测试

模拟前端 bone-system-app (src/services/api.ts) 对后端 bone-system 的完整调用链，
验证「功能设计合理、可正常使用」：
  - 认证：IAM 登录拿 token；system 接口带 token 200 / 不带 401
  - 配置主链路：分页参数对齐(pageNum/pageSize 后端生效) + 列表含 list/total + CRUD 闭环
  - 字典 / 日志 / 告警 / 控制台 / 系统 / 定时任务 主链路只读验证
  - 字典 / 定时任务 创建-切换-删除闭环（需 --allow-db-write）

用法:
  python3 scripts/verify-system-app-integration.py                # 只读联调
  python3 scripts/verify-system-app-integration.py --allow-db-write  # 含写操作闭环

依赖: 仅 Python 标准库(urllib)。需 bone-system(8083) 与 bone-iam(8081) 在运行。
"""
import sys
import json
import time
import random
import urllib.request
import urllib.error
import urllib.parse

SYS = "http://localhost:8083/api/v1"
IAM = "http://localhost:8081/api/v1"

results = []
created_ids = []  # [(kind, id)]


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


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name}" + (f" -- {detail}" if detail else ""))


def list_ok(d):
    """前端读 response.data.list / response.data.total：后端 PageResult 须同时含这两个字段"""
    return bool(d) and isinstance(d.get("data"), dict) and ("list" in d["data"]) and ("total" in d["data"])


def main():
    allow_write = "--allow-db-write" in sys.argv
    rand = f"{int(time.time())}_{random.randint(1000, 9999)}"

    print("=" * 64)
    print("bone-system ↔ bone-system-app 前后端联调")
    print("=" * 64)
    print(f"system={SYS}  iam={IAM}  allow_db_write={allow_write}")

    # ---------- 认证 ----------
    st, login, err = http("POST", IAM, "/iam/login", body={"username": "admin", "password": "123456"})
    token = None
    if login and isinstance(login.get("data"), dict):
        token = login["data"].get("token")
    check("A1 登录 IAM 获取 token", bool(token), f"status={st}")
    if not token:
        print("无法获取 token，终止")
        return 1

    st, _, _ = http("GET", SYS, "/system/config/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("A2 带 token 访问配置列表 200", st == 200, f"status={st}")
    st0, _, _ = http("GET", SYS, "/system/config/page", params={"pageNum": 1, "pageSize": 10})
    check("A3 无 token 访问返回 401", st0 == 401, f"status={st0}")

    # ---------- 配置主链路 ----------
    st, d, _ = http("GET", SYS, "/system/config/page", params={"pageNum": 1, "pageSize": 3}, token=token)
    data = d.get("data") if d else None
    check("B1 配置列表响应含 list/total 字段", list_ok(d), f"keys={list(data.keys()) if data else None}")
    ok_size = bool(data) and data.get("size") == 3 and len(data.get("list") or []) <= 3
    check("B2 分页参数对齐: pageSize=3 → size=3 且 list<=3",
          ok_size, f"size={data.get('size') if data else None}, len={len(data.get('list') or []) if data else None}")

    if allow_write:
        uk = f"it_e2e_{rand}"
        st, d, _ = http("POST", SYS, "/system/config",
                        body={"configKey": uk, "configValue": "e2e-value", "configType": "SYSTEM",
                              "description": "e2e 联调探针"}, token=token)
        cid = (d.get("data") if d else None)
        ok_create = st == 200 and isinstance(cid, int) and cid > 0
        check("B3 创建配置成功返回 id", ok_create, f"status={st}, id={cid}")
        if ok_create:
            created_ids.append(("config", cid))
            st, d, _ = http("GET", SYS, "/system/config/page", params={"keyword": uk, "pageNum": 1, "pageSize": 10}, token=token)
            hit = bool(d and d.get("data") and any(x.get("configKey") == uk for x in (d["data"].get("list") or [])))
            check("B4 列表按 keyword 命中新配置", hit)
            st, _, _ = http("PUT", SYS, "/system/config",
                            body={"id": cid, "configKey": uk, "configValue": "e2e-updated", "configType": "SYSTEM"}, token=token)
            check("B5 更新配置 200", st == 200, f"status={st}")
            st, d, _ = http("GET", SYS, f"/system/config/key/{uk}", token=token)
            ok_val = bool(d and d.get("data") and d["data"].get("configValue") == "e2e-updated")
            check("B6 按 key 查配置值已更新", ok_val, f"value={(d.get('data') or {}).get('configValue')}")
            st, _, _ = http("DELETE", SYS, f"/system/config/{cid}", token=token)
            check("B7 删除配置 200", st == 200, f"status={st}")
            st, d, _ = http("GET", SYS, "/system/config/page", params={"keyword": uk, "pageNum": 1, "pageSize": 10}, token=token)
            gone = bool(d and d.get("data") and not any(x.get("configKey") == uk for x in (d["data"].get("list") or [])))
            check("B8 删除后列表不再可见", gone)

    # ---------- 字典 ----------
    st, d, _ = http("GET", SYS, "/system/dicts/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("C1 字典列表响应含 list/total 字段", list_ok(d), f"status={st}")
    dtype = None
    if d and d.get("data") and (d["data"].get("list") or []):
        dtype = d["data"]["list"][0].get("type")
    if dtype:
        st, d, _ = http("GET", SYS, f"/system/dicts/type/{dtype}", token=token)
        ok_t = bool(d and d.get("code") == 200 and isinstance(d.get("data"), list))
        check("C2 按 type 查字典", ok_t, f"type={dtype}, status={st}")
    else:
        check("C2 按 type 查字典", True, "列表为空，跳过")

    if allow_write:
        ut = f"IT_E2E_{rand}"
        st, d, _ = http("POST", SYS, "/system/dicts",
                        body={"type": ut, "code": f"c_{rand}", "label": "e2e", "value": "1", "status": 1}, token=token)
        did = d.get("data") if d else None
        if st == 200 and isinstance(did, int) and did > 0:
            created_ids.append(("dict", did))
            check("C3 创建字典成功返回 id", True, f"id={did}")
            st, _, _ = http("DELETE", SYS, f"/system/dicts/{did}", token=token)
            check("C4 删除字典 200", st == 200, f"status={st}")
        else:
            check("C3 创建字典成功返回 id", False, f"status={st}, data={d}")

    # ---------- 日志 ----------
    st, d, _ = http("GET", SYS, "/system/logs/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("D1 日志列表响应含 list/total 字段", list_ok(d), f"status={st}")

    # ---------- 告警 ----------
    st, d, _ = http("GET", SYS, "/system/alert/rules/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("E1 告警规则列表含 list/total", list_ok(d), f"status={st}")
    st, d, _ = http("GET", SYS, "/system/alert/events/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("E2 告警事件列表含 list/total", list_ok(d), f"status={st}")

    # ---------- 控制台 ----------
    for ep in ["/console/overview", "/console/services", "/console/resources", "/console/metrics", "/console/quick-actions"]:
        st, _, _ = http("GET", SYS, ep, token=token)
        check(f"F {ep} 200", st == 200, f"status={st}")

    # ---------- 系统 ----------
    for ep in ["/system/health", "/system/info", "/system/metrics"]:
        st, d, _ = http("GET", SYS, ep, token=token)
        check(f"G {ep} 200", st == 200, f"status={st}")

    # ---------- 定时任务 ----------
    st, d, _ = http("GET", SYS, "/system/schedule-tasks/page", params={"pageNum": 1, "pageSize": 10}, token=token)
    check("H1 定时任务列表含 list/total 字段", list_ok(d), f"status={st}")

    if allow_write:
        un = f"it_e2e_task_{rand}"
        st, d, _ = http("POST", SYS, "/system/schedule-tasks",
                        body={"name": un, "cron": "0 0/1 * * * ?", "handler": "e2eHandler", "status": "DISABLED"}, token=token)
        tid = d.get("data") if d else None
        if st == 200 and isinstance(tid, int) and tid > 0:
            created_ids.append(("task", tid))
            check("H2 创建定时任务成功返回 id", True, f"id={tid}")
            st, _, _ = http("PUT", SYS, f"/system/schedule-tasks/{tid}/toggle", params={"enabled": True}, token=token)
            check("H3 切换定时任务启用 200", st == 200, f"status={st}")
            st, _, _ = http("DELETE", SYS, f"/system/schedule-tasks/{tid}", token=token)
            check("H4 删除定时任务 200", st == 200, f"status={st}")
        else:
            check("H2 创建定时任务成功返回 id", False, f"status={st}, data={d}")

    # ---------- 汇总 ----------
    print("=" * 64)
    npass = sum(1 for _, ok, _ in results if ok)
    nfail = len(results) - npass
    print(f"总计 {len(results)} 项: PASS={npass}  FAIL={nfail}")
    for name, ok, detail in results:
        if not ok:
            print(f"  FAIL -> {name}  {detail}")
    print("=" * 64)
    return 0 if nfail == 0 else 2


if __name__ == "__main__":
    try:
        rc = main()
    finally:
        # 清理临时数据，确保不污染库
        if created_ids:
            # 重新登录拿 token（main 内 token 作用域外），简单复用：重试登录
            try:
                _, lg, _ = http("POST", IAM, "/iam/login", body={"username": "admin", "password": "123456"})
                tk = lg.get("data", {}).get("token") if lg else None
                for kind, cid in created_ids:
                    path = {"config": f"/system/config/{cid}", "dict": f"/system/dicts/{cid}",
                            "task": f"/system/schedule-tasks/{cid}"}.get(kind)
                    if path and tk:
                        http("DELETE", SYS, path, token=tk)
                print(f"[cleanup] 已清理临时数据 {len(created_ids)} 条")
            except Exception as e:  # noqa: BLE001
                print(f"[cleanup] 清理失败(需手动): {e}  created_ids={created_ids}")
    sys.exit(rc if 'rc' in dir() else 2)
