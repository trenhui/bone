#!/usr/bin/env python3
"""
界面设计器字典打通 v1.1 —— B/C/D 端到端验证（租户灰度开关 / 三个可观测指标 / L0→L1 迁移向导）。

运行前置：bone-iam(8081)、bone-system(8083)、bone-metadata-server(9001) 均在跑（无需网关）。
判据纪律：
  * 每个断言自问「删掉实现哪一行会红」——纯正向断言不足，故 B 有「关闭后创建被拒」负向、
    D 有「未确认冲突 409」与「重复迁移 409」负向；
  * 关闭态必须证明「不发起解析」而不只是「返回旧值」（options/version 双 null + metrics 不计数）。
退出码：0 全通过；1 有失败。
"""
import json
import sys
import time
import urllib.error
import urllib.request

IAM = "http://127.0.0.1:8081"
SYS = "http://127.0.0.1:8083"
META = "http://127.0.0.1:9001"
TENANT = "0"

passed = 0
failed = []


def call(method, url, body=None, token=None, expect=None, tenant=True):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if tenant:
        req.add_header("X-Tenant-Id", TENANT)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data, timeout=20) as r:
            code, raw = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        code, raw = e.code, e.read().decode()
    try:
        parsed = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        parsed = {"raw": raw}
    if expect is not None:
        allowed = expect if isinstance(expect, (tuple, list, set)) else (expect,)
        if code not in allowed:
            raise AssertionError(f"{method} {url} 期望 {allowed} 实际 {code}: {raw[:200]}")
    return code, parsed


def check(name, ok, detail=""):
    global passed
    if ok:
        passed += 1
        print(f"  ✅ {name}")
    else:
        failed.append(name)
        print(f"  ❌ {name}  {detail}")


def err_code(resp):
    data = resp.get("data")
    if isinstance(data, dict):
        return data.get("errorCode") or data.get("code")
    return None


def as_int(v):
    """后端 Long 一律序列化为字符串（项目全局约定），断言前必须显式转换。"""
    if v is None or v == "":
        return 0
    return int(v)


def login():
    _, r = call(
        "POST",
        f"{IAM}/api/v1/iam/login",
        {"username": "admin", "password": "123456"},
        tenant=False,
    )
    token = (r.get("data") or {}).get("token")
    if not token:
        raise SystemExit(f"登录失败：{r}")
    return token


def create_entity(token, code_hint):
    rc = int(time.time() * 1000) % 1000000
    code = f"{code_hint}_{rc}"
    code_, r = call(
        "POST",
        f"{META}/api/v1/metadata/entities",
        {
            "name": "治理验证实体",
            "code": code,
            "displayName": "治理验证实体",
            "tableName": f"t_{code}",
            "type": 1,
            "deliveryMode": 1,
        },
        token,
        expect=(200, 201),
    )
    data = r.get("data")
    eid = data.get("id") if isinstance(data, dict) else data
    if not eid:
        raise SystemExit(f"建实体失败：{code_} {r}")
    return eid, code


def create_local_enum_field(token, eid, code, options):
    """L0 字段：本地 enumValues，无 enum_source。"""
    _, r = call(
        "POST",
        f"{META}/api/v1/metadata/entities/{eid}/fields",
        {
            "name": code,
            "code": code,
            "displayName": code,
            "type": "ENUM",
            "enumValues": json.dumps(options, ensure_ascii=False),
        },
        token,
        expect=(200, 201),
    )
    data = r.get("data")
    fid = data.get("id") if isinstance(data, dict) else data
    if not fid:
        raise SystemExit(f"建 L0 字段失败：{r}")
    return fid


def create_dict_field(token, eid, code, type_code):
    """L1 绑定字段（enum_source 必须是 JSON 字符串）。"""
    decl = {"type": "dict", "mode": "snapshot", "ref": {"typeCode": type_code}}
    return call(
        "POST",
        f"{META}/api/v1/metadata/entities/{eid}/fields",
        {
            "name": code,
            "code": code,
            "displayName": code,
            "type": "ENUM",
            "enumSource": json.dumps(decl),
        },
        token,
    )


def main():
    token = login()

    # ================= setup：两个值域 =================
    suffix = str(int(time.time()))[-6:]
    T_CONFLICT = f"GOV_C{suffix}"    # 迁移冲突值域：M=男, F=女性, Y=未知
    T_MERGE = f"GOV_M{suffix}"       # 并入值域：A=甲
    for code, name in ((T_CONFLICT, "治理冲突值域"), (T_MERGE, "治理并入值域")):
        call(
            "POST",
            f"{SYS}/api/v1/system/dict/types",
            {
                "code": code,
                "name": name,
                "category": "LIST",
                "valueType": "STRING",
                "status": 1,
                "sort": 0,
            },
            token,
            expect=200,
        )
    for tc, c, label in (
        (T_CONFLICT, "M", "男"),
        (T_CONFLICT, "F", "女性"),
        (T_CONFLICT, "Y", "未知"),
        (T_MERGE, "A", "甲"),
    ):
        call(
            "POST",
            f"{SYS}/api/v1/system/dict/items",
            {"typeCode": tc, "code": c, "label": label, "value": c, "status": 1, "sort": 0},
            token,
            expect=200,
        )
    print(f"[setup] 值域就绪：{T_CONFLICT}=M/F/Y，{T_MERGE}=A")

    eid, ent_code = create_entity(token, "gov")
    print(f"[setup] 实体 {ent_code} (id={eid})")

    # ================= B：租户级灰度开关 =================
    print("\n=== B 租户级灰度开关（v1.1 §6.5） ===")
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/toggle", token=token, expect=200)
    st = r.get("data") or {}
    check("B1 默认开放（本地配置 enabled=true），来源 global-default",
          st.get("enabled") is True and st.get("source") == "global-default", f"{st}")

    # 先建一个绑定字段，作为「关闭态读路径」的观测点
    _, r = create_dict_field(token, eid, "status_bound", T_CONFLICT)
    bound_fid = (r.get("data") or {}).get("id") if isinstance(r.get("data"), dict) else r.get("data")
    check("B2 开放态可建绑定字段并物化快照", bool(bound_fid), f"{r}")

    _, r = call("PUT", f"{META}/api/v1/metadata/dict-binding/toggle?enabled=false", token=token, expect=200)
    st_off = r.get("data") or {}
    check("B3 运行时关闭：enabled=false 且来源 runtime（覆盖优先于配置）",
          st_off.get("enabled") is False and st_off.get("source") == "runtime", f"{st_off}")

    _, r = call(
        "GET",
        f"{META}/api/v1/metadata/fields/{bound_fid}/options/version",
        token=token,
        expect=200,
    )
    ver = r.get("data") or {}
    check("B4 关闭态读路径视为未绑定（source/snapshot 版本双 null，不发起解析）",
          ver.get("sourceVersion") is None and ver.get("snapshotVersion") is None, f"{ver}")

    code_, r = create_dict_field(token, eid, "status_off", T_CONFLICT)
    check("B5 关闭态写入口拒绝 dict 声明（400 META_ENUM_SOURCE_INVALID）",
          code_ == 400 and err_code(r) == "META_ENUM_SOURCE_INVALID", f"HTTP {code_}: {r}")

    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m_off = r.get("data") or {}
    check("B6 关闭态指标把绑定字段算作未绑定（boundEnumFields=0）",
          as_int(m_off.get("boundEnumFields")) == 0, f"{m_off}")

    _, r = call("DELETE", f"{META}/api/v1/metadata/dict-binding/toggle", token=token, expect=200)
    st_on = r.get("data") or {}
    check("B7 撤销覆盖 → 回落到配置判定（enabled=true / global-default）",
          st_on.get("enabled") is True and st_on.get("source") == "global-default", f"{st_on}")

    code_, r = create_dict_field(token, eid, "status_on", T_CONFLICT)
    ok_on = code_ in (200, 201) and bool((r.get("data") or {}).get("id") if isinstance(r.get("data"), dict) else r.get("data"))
    check("B8 恢复开放后同一声明可通过（正对照：拒绝确由开关驱动）", ok_on, f"HTTP {code_}: {r}")

    # ================= C：三个可观测指标 =================
    print("\n=== C 三个可观测指标（v1.1 §8） ===")
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m0 = r.get("data") or {}
    total = as_int(m0.get("totalEnumFields"))
    bound = as_int(m0.get("boundEnumFields"))
    drift0 = as_int(m0.get("driftPending"))
    resolves0 = as_int(m0.get("totalResolves"))
    fallback0 = as_int(m0.get("fallbackResolves"))
    check("C1 覆盖率分母是全量 ENUM（≥ 已绑定数），比例自洽",
          total >= bound >= 1 and abs(float(m0.get("bindingCoverage", -1)) - round(bound / total, 4)) < 1e-6,
          f"{m0}")

    # 共享 dev 库里已有历史漂移（早期探针留下的绑定），故一律用「增量」判定而非绝对值为 0——
    # 绝对值断言在脏库上恒红，在新库上恒绿，两边都没有判别力。
    _, r = create_dict_field(token, eid, "status_sync", T_CONFLICT)
    sync_fid = (r.get("data") or {}).get("id") if isinstance(r.get("data"), dict) else r.get("data")
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m1 = r.get("data") or {}
    check("C2 新建绑定字段（快照与真源同版本）不增加漂移待刷新数",
          as_int(m1.get("driftPending")) == drift0,
          f"before={drift0} after={m1.get('driftPending')}")

    # 真源新增一项 → 该字段转为漂移：drift 必须上升（版本协商能看见差异）
    call(
        "POST",
        f"{SYS}/api/v1/system/dict/items",
        {"typeCode": T_CONFLICT, "code": "Z", "label": "新增项", "value": "Z", "status": 1, "sort": 9},
        token,
        expect=200,
    )
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m2 = r.get("data") or {}
    check("C3 真源变更后漂移待刷新数上升（版本协商生效的证明）",
          as_int(m2.get("driftPending")) > drift0, f"before={drift0} after={m2.get('driftPending')}")

    # 刷新该字段快照 → 漂移回落
    call("POST", f"{META}/api/v1/metadata/fields/{sync_fid}/options/refresh", {}, token, expect=200)
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m3 = r.get("data") or {}
    check("C4 刷新后漂移回落（版本比对与刷新链路闭环）",
          as_int(m3.get("driftPending")) < as_int(m2.get("driftPending")),
          f"before={m2.get('driftPending')} after={m3.get('driftPending')}")

    # 降级率：显式走一次读路径解析（设计器预览 /options），健康时应计入 total 而不计入 fallback
    _, r = call("GET", f"{META}/api/v1/metadata/fields/{sync_fid}/options", token=token, expect=200)
    _, r = call("GET", f"{META}/api/v1/metadata/dict-binding/metrics", token=token, expect=200)
    m4 = r.get("data") or {}
    check("C5 健康解析计入总数（/options 读路径确实被度量，totalResolves 上升）",
          as_int(m4.get("totalResolves")) > resolves0,
          f"before={resolves0} after={m4.get('totalResolves')}")
    check("C6 健康解析不计入降级（fallbackResolves 不变、降级率为 0）",
          as_int(m4.get("fallbackResolves")) == fallback0 and float(m4.get("fallbackRate", -1)) == 0,
          f"fallback={m4.get('fallbackResolves')} rate={m4.get('fallbackRate')}")

    # ================= D：L0→L1 迁移向导 =================
    print("\n=== D L0→L1 迁移向导（v1.1 §5.3） ===")
    l0_fid = create_local_enum_field(
        token,
        eid,
        "gender_local",
        [
            {"code": "M", "label": "男"},
            {"code": "F", "label": "女"},
            {"code": "X", "label": "未知"},
        ],
    )
    _, r = call(
        "GET",
        f"{META}/api/v1/metadata/fields/{l0_fid}/migrate-to-dict/preview?typeCode={T_CONFLICT}",
        token=token,
        expect=200,
    )
    plan = r.get("data") or {}
    check("D1 预览分类：完全一致 M", plan.get("matched") == ["M"], f"{plan.get('matched')}")
    check("D2 预览分类：真源多出不含本地项（Y 与新增 Z）",
          set(plan.get("dictOnly") or []) == {"Y", "Z"}, f"{plan.get('dictOnly')}")
    check("D3 预览分类：本地多出 X", plan.get("localOnly") == ["X"], f"{plan.get('localOnly')}")
    check("D4 同码不同义被识别：F 本地「女」vs 真源「女性」",
          len(plan.get("labelConflicts") or []) == 1
          and plan["labelConflicts"][0]["code"] == "F"
          and plan["labelConflicts"][0]["localLabel"] == "女"
          and plan["labelConflicts"][0]["dictLabel"] == "女性",
          f"{plan.get('labelConflicts')}")
    check("D5 同义不同码被识别：X（本地未知）↔ Y（真源未知）",
          any(c.get("localCode") == "X" for c in (plan.get("codeConflicts") or [])),
          f"{plan.get('codeConflicts')}")
    check("D6 有冲突 → requiresConfirmation=true（告警不阻断但要确认）",
          plan.get("requiresConfirmation") is True, f"{plan}")

    code_, r = call(
        "POST",
        f"{META}/api/v1/metadata/fields/{l0_fid}/migrate-to-dict",
        {"typeCode": T_CONFLICT, "localOnlyStrategy": "KEEP_LOCAL_OVERRIDE", "confirmConflicts": False},
        token,
    )
    check("D7 冲突未确认 → 409 META_MIGRATE_CONFLICT_UNCONFIRMED",
          code_ == 409 and err_code(r) == "META_MIGRATE_CONFLICT_UNCONFIRMED", f"HTTP {code_}: {r}")

    _, r = call(
        "POST",
        f"{META}/api/v1/metadata/fields/{l0_fid}/migrate-to-dict",
        {"typeCode": T_CONFLICT, "localOnlyStrategy": "KEEP_LOCAL_OVERRIDE", "confirmConflicts": True},
        token,
        expect=200,
    )
    res = r.get("data") or {}
    check("D8 KEEP_LOCAL_OVERRIDE：本地多出项保留在快照（keptLocalOverrides=1）",
          res.get("keptLocalOverrides") == 1 and res.get("mergedIntoDict") == 0, f"{res}")
    check("D9 KEEP 策略快照 = 真源 4 项 + 本地 1 项",
          res.get("snapshotCount") == 5, f"{res}")

    _, r = call("GET", f"{META}/api/v1/metadata/fields/{l0_fid}/options/version", token=token, expect=200)
    ver3 = r.get("data") or {}
    check("D10 迁移后绑定生效：快照版本已写入（非 null）",
          ver3.get("snapshotVersion") is not None, f"{ver3}")

    code_, r = call(
        "POST",
        f"{META}/api/v1/metadata/fields/{l0_fid}/migrate-to-dict",
        {"typeCode": T_CONFLICT, "localOnlyStrategy": "KEEP_LOCAL_OVERRIDE", "confirmConflicts": True},
        token,
    )
    check("D11 重复迁移被拒（409 META_MIGRATE_ALREADY_BOUND）",
          code_ == 409 and err_code(r) == "META_MIGRATE_ALREADY_BOUND", f"HTTP {code_}: {r}")

    # MERGE 分支：真源只有 A，本地有 A+B → B 并入真源
    l0_merge = create_local_enum_field(
        token, eid, "merge_local", [{"code": "A", "label": "甲"}, {"code": "B", "label": "乙"}]
    )
    _, r = call(
        "POST",
        f"{META}/api/v1/metadata/fields/{l0_merge}/migrate-to-dict",
        {"typeCode": T_MERGE, "localOnlyStrategy": "MERGE_INTO_DICT", "confirmConflicts": True},
        token,
        expect=200,
    )
    mres = r.get("data") or {}
    check("D12 MERGE_INTO_DICT：缺失项写入真源（mergedIntoDict=1）",
          mres.get("mergedIntoDict") == 1, f"{mres}")
    _, r = call("GET", f"{SYS}/api/v1/system/dict/items/options?type={T_MERGE}", token=token, expect=200)
    codes = {o.get("code") for o in (r.get("data") or [])}
    check("D13 并入后真源确实含 B（不是只改了本地快照）", "B" in codes, f"codes={codes}")
    check("D14 MERGE 策略不保留本地追加（快照=真源 2 项）",
          mres.get("keptLocalOverrides") == 0 and mres.get("snapshotCount") == 2, f"{mres}")

    l0_bad = create_local_enum_field(token, eid, "bad_target", [{"code": "A", "label": "甲"}])
    code_, r = call(
        "GET",
        f"{META}/api/v1/metadata/fields/{l0_bad}/migrate-to-dict/preview?typeCode=NOT_EXIST_{suffix}",
        token=token,
    )
    check("D15 目标值域不存在 → 502 META_MIGRATE_TARGET_UNRESOLVED（不做无依据 diff）",
          code_ == 502 and err_code(r) == "META_MIGRATE_TARGET_UNRESOLVED", f"HTTP {code_}: {r}")

    print(f"\n===== 结果：{passed} 通过 / {len(failed)} 失败 =====")
    if failed:
        for f in failed:
            print(f"  - FAILED: {f}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
