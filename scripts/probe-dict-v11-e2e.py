#!/usr/bin/env python3
"""字典打通 v1.1 前后端联调探针（经网关 8888 全链路）。

覆盖（doc/design/lowcode/ui/界面设计器字典打通设计方案.md v1.1）：
  S1 三态生命周期（bone-system）：deprecate/restore/disable + /options 三态口径 + DELETE 破坏性变更拦截
  S2 绑定物化与漂移（metadata-server）：enum_source 物化带 state、option-diff 四类差异、dict-bindings 引用方清单
  S3 负向判据：未绑定值域返回空清单；越界 DELETE 拒绝

纪律：前置动作失败即中止（禁假绿）；每个断言锚定「删掉实现会红」的性质；末尾汇总，失败 exit 1。
"""
import json
import sys
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8888"
# 每轮唯一编码（时间戳后缀）：探针可重复执行，上一轮残留不会污染「重复创建」与「len(binds)==1」断言
_RUN = time.strftime("%Y%m%d_%H%M%S")
TYPE_CODE = f"E2E_DICT_V11_{_RUN}"
ENTITY_CODE = f"e2e_v11_entity_{_RUN}"

passed = []
failed = []


def check(name, cond, detail=""):
    (passed if cond else failed).append((name, detail))
    mark = "PASS" if cond else "FAIL"
    print(f"  [{mark}] {name}" + (f" — {detail}" if detail and not cond else ""))


def call(method, path, body=None, token=None, expect=None):
    """expect 可为 int 或可迭代集合（如 (200, 201)——metadata 创建端点 REST 正确地返回 201）。"""
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data, timeout=15) as resp:
            payload = json.loads(resp.read().decode())
            code = resp.status
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode() or "{}")
        code = e.code
    if expect is not None:
        allowed = expect if isinstance(expect, (tuple, list, set)) else (expect,)
        if code not in allowed:
            raise AssertionError(f"{method} {path} => HTTP {code} (expect {allowed}): {payload}")
    return code, payload


def rejected(resp, want_code, want_error_code):
    """负向判据：HTTP 状态码 + 业务错误码双双命中（拒绝必须可辨识，不能只靠状态码）。"""
    code, payload = resp
    blob = json.dumps(payload, ensure_ascii=False)
    return code == want_code and want_error_code in blob


def extract_id(resp):
    """平台契约：Long/雪花 ID 在 JSON 中序列化为字符串——data 可能是 id 字符串，也可能是含 id 字段的对象。"""
    d = resp.get("data")
    if isinstance(d, dict):
        return d.get("id") or d.get("fieldId") or d.get("entityId")
    return d


def main():
    # ---------- 登录（前置动作，失败即中止） ----------
    code, resp = call("POST", "/api/v1/iam/login", {"username": "admin", "password": "123456"})
    token = (resp.get("data") or {}).get("token")
    if not token:
        print("FATAL: 登录失败，探针中止")
        sys.exit(1)
    print(f"[setup] 登录成功（tenant 链路就绪）")

    # ---------- S1 字典三态生命周期 ----------
    print("\n=== S1 三态生命周期（bone-system） ===")
    call("POST", "/api/v1/system/dict/types", {
        "code": TYPE_CODE, "name": "E2E字典打通v1.1", "category": "LIST",
        "valueType": "STRING", "status": 1, "sort": 0,
    }, token, expect=200)

    ids = {}
    for c in ("A", "B", "C"):
        _, r = call("POST", "/api/v1/system/dict/items", {
            "typeCode": TYPE_CODE, "code": c, "label": f"标签{c}", "value": c,
            "status": 1, "sort": ord(c) - 64,
        }, token, expect=200)
        ids[c] = extract_id(r)
    print(f"[setup] 字典类型 {TYPE_CODE} + 3 项（A/B/C）就绪")

    _, r = call("PUT", f"/api/v1/system/dict/items/{ids['B']}/deprecate", {}, token, expect=200)
    _, r = call("GET", f"/api/v1/system/dict/items/{TYPE_CODE}/B", token=token)
    check("S1.1 deprecate 后 status=2（DEPRECATED）", (r.get("data") or {}).get("status") == 2,
          f"status={(r.get('data') or {}).get('status')}")

    _, r = call("GET", f"/api/v1/system/dict/items/options?type={TYPE_CODE}", token=token)
    opts = r.get("data") or []
    b = next((o for o in opts if o.get("code") == "B"), None)
    check("S1.2 /options 返回 DEPRECATED 项且带 state（写侧放行凭证）",
          b is not None and b.get("state") == "DEPRECATED", f"B={b}")
    check("S1.3 /options 仍返回 ACTIVE 项（渲染口径双轨）",
          all(any(o.get("code") == c for o in opts) for c in ("A", "C")))

    # 负向：未停用即删 → 409 + DICT_ITEM_DELETE_REQUIRES_DISABLED（breaking 必须先过渡，§4.4 管理面强制；
    # 错误码实证自 DictApplicationService#deleteItem，映射 409 见 SystemErrors）
    resp = call("DELETE", f"/api/v1/system/dict/items/{ids['B']}", token=token)
    check("S1.4 DELETE 未停用项被拒（破坏性变更拦截）",
          rejected(resp, 409, "SYS_DICT_ITEM_DELETE_REQUIRES_DISABLED"),
          f"HTTP {resp[0]}: {resp[1]}")

    _, r = call("PUT", f"/api/v1/system/dict/items/{ids['B']}/restore", {}, token, expect=200)
    _, r = call("GET", f"/api/v1/system/dict/items/{TYPE_CODE}/B", token=token)
    check("S1.5 restore 后 status=1（回到 ACTIVE）", (r.get("data") or {}).get("status") == 1)

    # 负向：ACTIVE 项不可「恢复」（非法迁移）——DICT_ITEM_STATE_TRANSITION_INVALID 映射 409（SystemErrors）
    resp = call("PUT", f"/api/v1/system/dict/items/{ids['A']}/restore", {}, token)
    check("S1.6 ACTIVE 项 restore 被拒（非法状态迁移）",
          rejected(resp, 409, "SYS_DICT_ITEM_STATE_TRANSITION_INVALID"),
          f"HTTP {resp[0]}: {resp[1]}")

    call("PUT", f"/api/v1/system/dict/items/{ids['B']}", {"status": 0}, token, expect=200)
    call("DELETE", f"/api/v1/system/dict/items/{ids['B']}", token=token, expect=200)
    print("[setup] B 已停用并删除（终态后才可删）")

    # ---------- S2 绑定物化与漂移 ----------
    print("\n=== S2 绑定物化与漂移（metadata-server） ===")
    _, r = call("POST", "/api/v1/metadata/entities", {
        "name": "E2E字典打通实体", "code": ENTITY_CODE, "displayName": "E2E字典打通实体",
        "tableName": "t_" + ENTITY_CODE, "type": 1, "deliveryMode": 1,
    }, token, expect=(200, 201))
    entity_id = extract_id(r)
    print(f"[setup] 实体 {ENTITY_CODE}(id={entity_id}) 就绪")

    _, r = call("POST", f"/api/v1/metadata/entities/{entity_id}/fields", {
        "name": "标签", "code": "label", "displayName": "标签", "type": "ENUM",
        "enumSource": json.dumps({"type": "dict", "mode": "snapshot", "ref": {"typeCode": TYPE_CODE}}),
    }, token, expect=(200, 201))
    field_id = extract_id(r)
    print(f"[setup] ENUM 字段已绑定 {TYPE_CODE}（fieldId={field_id}）")

    _, r = call("GET", f"/api/v1/metadata/entities/{entity_id}/fields/{field_id}", token=token)
    fdata = r.get("data") or {}
    snap = json.loads(fdata.get("enumValues") or "[]")
    codes = [o.get("code") for o in snap]
    check("S2.1 物化快照含真源选项（A/C）", "A" in codes and "C" in codes, f"codes={codes}")
    check("S2.2 物化快照不含已删除项 B（快照即发布口径）", "B" not in codes, f"codes={codes}")

    _, r = call("GET", f"/api/v1/metadata/fields/{field_id}/option-diff", token=token)
    diff = r.get("data") or {}
    check("S2.3 初态无漂移（resolved 且 added 为空）",
          diff.get("resolved") is True and not diff.get("added"), f"diff={diff}")

    # 真源变更：新增 D + 弃用 A → 漂移四类中的 added/stateChanged
    call("POST", "/api/v1/system/dict/items", {
        "typeCode": TYPE_CODE, "code": "D", "label": "标签D", "value": "D", "status": 1, "sort": 4,
    }, token, expect=200)
    call("PUT", f"/api/v1/system/dict/items/{ids['A']}/deprecate", {}, token, expect=200)
    _, r = call("GET", f"/api/v1/metadata/fields/{field_id}/option-diff", token=token)
    diff = r.get("data") or {}
    check("S2.4 漂移检测新增项（added=[D]）", diff.get("added") == ["D"], f"added={diff.get('added')}")
    check("S2.5 漂移检测状态变迁（stateChanged=[A]，渲染集会少一项）",
          diff.get("stateChanged") == ["A"], f"stateChanged={diff.get('stateChanged')}")

    _, r = call("GET", f"/api/v1/metadata/fields/dict-bindings?typeCode={TYPE_CODE}", token=token)
    binds = r.get("data") or []
    check("S2.7 引用方清单返回该绑定", len(binds) == 1 and str(binds[0].get("fieldId")) == str(field_id),
          f"bindings={binds}")
    if binds:
        check("S2.6 漂移汇总含两类计数（driftSummary）",
              "新增 1" in (binds[0].get("driftSummary") or "") and "状态变迁 1" in (binds[0].get("driftSummary") or ""),
              f"driftSummary={binds[0].get('driftSummary')}")
        check("S2.8 绑定漂移状态为真（待刷新）", binds[0].get("drift") is True, f"drift={binds[0].get('drift')}")
        check("S2.9 绑定带实体名（影响预览可读）", binds[0].get("entityName") == "E2E字典打通实体",
              f"entityName={binds[0].get('entityName')}")

    _, r = call("GET", f"/api/v1/metadata/fields/dict-bindings?typeCode=SYS_STATUS", token=token)
    check("S3.1 未绑定值域返回空清单（不误报）", (r.get("data") or []) == [], f"data={r.get('data')}")

    # ---------- S2 收尾：物化带 state（刷新快照后 DEPRECATED 项须带 state 标记） ----------
    # 设计性质：ACTIVE 项不写 state（字节级兼容存量快照），DEPRECATED 项写 state=DEPRECATED（写侧放行凭证）
    call("POST", f"/api/v1/metadata/fields/{field_id}/options/refresh", {}, token, expect=200)
    _, r = call("GET", f"/api/v1/metadata/entities/{entity_id}/fields/{field_id}", token=token)
    snap = json.loads((r.get("data") or {}).get("enumValues") or "[]")
    by_code = {o.get("code"): o for o in snap}
    check("S2.10 刷新后 DEPRECATED 项带 state 标记（写侧放行凭证）",
          (by_code.get("A") or {}).get("state") == "DEPRECATED", f"A={by_code.get('A')}")
    check("S2.11 刷新后快照纳入新增项 D", "D" in by_code, f"codes={list(by_code)}")
    check("S2.12 刷新后 ACTIVE 项不写 state（字节级兼容存量快照）",
          (by_code.get("C") or {}).get("state") is None, f"C={by_code.get('C')}")

    # ---------- 汇总 ----------
    print(f"\n=== 汇总：{len(passed)} passed, {len(failed)} failed ===")
    for name, detail in failed:
        print(f"  FAIL: {name} — {detail}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
