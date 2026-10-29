#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""设备巡检工单场景：应用创建 → 模块添加 → 模型设计 → 界面设计 → 发布 → 数据全生命周期（经网关，模拟真人建模与业务操作）。

与 scripts/e2e/two-scenario-full-chain-e2e.py 的分工：
  那份覆盖「报销/理赔/采购」三样板的**模型深化能力**（行策略/动作定义/状态机/回收站/审计），
  实体直接挂在既有模块下；本脚本覆盖的是**从零开始的完整生命周期第一公里**：
    ① 应用创建（/api/v1/apps，IAM 域）         —— 以前两份 E2E 都没碰过这一跳
    ② 模块添加（/apps/{appId}/modules）        —— 实体的归属容器
    ③ 模型设计（实体 + 8 字段，ENUM/DATE/TEXT/INTEGER 多类型）
    ④ 界面设计（ui-schema 草稿 PUT → validate → 发布闸门负向 → 正式 schema → 发布）
    ⑤ 数据保存/查看/更新/并发保护/软删回收（runtime 记录全生命周期）
    ⑥ 列表展示（列投影 fields 契约 + q 精确筛选）
    ⑦ MySQL 落库核查（API 之外的第二证据源，防「接口假绿」）

每一步都带负向探针（断言有牙）：缺参 400、编码冲突 409、发布闸门 409、
坏 ETag 412、缺必填 4xx —— 删掉对应实现，这些断言必须变红。

产物：/tmp/bone_e2e_fullchain_state.json（供 scripts/e2e/ui/ui-app-to-data-probe.mjs 消费）。

退出时默认自动清理本次运行在库里的全部残留（cleanup-residue.py，见下方 teardown.register）。
因此：接续跑 UI 探针前请用 BONE_E2E_KEEP_RESIDUE=1 启动本脚本保留产物，
探针消费完 state 后再手工补清理：
  python3 scripts/e2e/cleanup-residue.py --suffix <后缀> --apply

用法：python3 scripts/e2e/app-to-data-full-chain-e2e.py            # 默认走网关 :8888
      BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 python3 ...       # 直连后端排查用
      BONE_E2E_KEEP_RESIDUE=1 python3 ...                           # 保留残留（供 UI 探针消费）

基础设施（登录 / HTTP / 断言 / 建模与业务助手 / teardown / MySQL 直查）统一来自
scripts/e2e/e2e_lib/ 公共层，本脚本只保留场景专属的数据构造与断言。
"""

import json
import os

from e2e_lib import config, http, mysql, report, teardown
from e2e_lib import fixtures as fx
from e2e_lib.http import call, d_, ecode, etag_of
from e2e_lib.modeling import put_schema, validate
from e2e_lib.report import check, note, section

SUFFIX = config.run_suffix()  # 运行级后缀：所有产物带它，teardown 按后缀定点回收

# 生产仿真数据集：同后缀 ⇒ 同数据。业务值一律在此生成一次、存入变量，
# API 创建 / 断言比对 / MySQL 落库核查三处共用同一变量（分头生成会对不上成假红）
ds = fx.dataset(SUFFIX)
CITY = ds.city    # 属地文案：应用名与描述用（自然中文业务文案）
DEPT = ds.dept    # 归口部门文案：模块/实体名与描述用

# 主会话（admin）登录：失败即退出——没登录跑什么都是假红
http.login()

APP_CODE = f"e2e_om_{SUFFIX}"
APP_NAME = f"{CITY}设备运维管理{SUFFIX}"
MODULE_CODE = f"e2e_insp_{SUFFIX}"
MODULE_NAME = f"{DEPT}巡检管理{SUFFIX}"
ENTITY_CODE = f"t_insp_order_{SUFFIX}"
TABLE_NAME = f"t_insp_order_{SUFFIX}"
ENTITY_NAME = f"{DEPT}设备巡检工单{SUFFIX}"

EQUIPMENT_TYPES = [
    {"code": "cnc", "label": "数控机床"},
    {"code": "boiler", "label": "蒸汽锅炉"},
    {"code": "conveyor", "label": "装配输送线"},
]
INSPECTION_RESULTS = [
    {"code": "normal", "label": "正常"},
    {"code": "abnormal", "label": "异常"},
    {"code": "recheck", "label": "待复检"},
]

# 8 字段覆盖 5 种控件类型：STRING/ENUM/DATE/TEXT/INTEGER
FIELDS = [
    {"code": "equipment_no", "label": "设备编号", "type": "STRING", "required": True, "length": 64},
    {"code": "equipment_name", "label": "设备名称", "type": "STRING", "required": True, "length": 128},
    {"code": "equipment_type", "label": "设备类型", "type": "ENUM", "required": True, "enumValues": EQUIPMENT_TYPES},
    {"code": "inspection_date", "label": "巡检日期", "type": "DATE", "required": True},
    {"code": "inspector", "label": "巡检人", "type": "STRING", "required": True, "length": 64},
    {"code": "inspection_result", "label": "巡检结果", "type": "ENUM", "required": True, "enumValues": INSPECTION_RESULTS},
    {"code": "abnormal_desc", "label": "异常描述", "type": "TEXT", "required": False},
    {"code": "duration_minutes", "label": "巡检工时(分钟)", "type": "INTEGER", "required": False},
]


# ---------------------------------------------------------------- 运行级残留 teardown
# 本次运行会在库里留下：e2e_om_<SUFFIX> 应用 / e2e_insp_<SUFFIX> 模块 / t_insp_order_<SUFFIX>
# 实体定义及字段、动态物理表与运行期记录。默认退出时（含异常/sys.exit）自动回收，
# 防止反复联调把库越堆越脏；BONE_E2E_KEEP_RESIDUE=1 跳过清理、保留现场供 UI 探针/排查。
def _drop_state():
    # 残已删则 state 文件必然过期：同步作废，防 UI 探针读到陈旧状态静默跑偏
    try:
        os.remove(config.STATE_PATH)
    except OSError:
        pass


# 与原实现口径一致：仅在清理成功后才作废 state 文件（on_clean 只于成功路径回调）
teardown.register(SUFFIX, on_clean=_drop_state)


# ---------------------------------------------------------------- ① 应用创建
section("① 应用创建（IAM 域 /api/v1/apps）")
s, d = call("POST", "/apps", {
    "name": APP_NAME, "code": APP_CODE,
    "description": f"{CITY}{DEPT}设备运维：巡检业务从建模到数据全生命周期",
    "icon": "SettingOutlined",
})
check("应用创建 201", s == 201, f"status={s} {str(d)[:200]}")
APP_ID = d.get("data")
# Long 雪花 ID 在 JSON 中序列化为字符串（平台约定），按数字串验收
check("应用创建返回雪花 ID", str(APP_ID or "").isdigit() and int(APP_ID) > 0, f"data={APP_ID}")

# 负向：缺 code（@NotBlank）→ 400
s, d = call("POST", "/apps", {"name": "缺编码应用"})
check("负向：应用缺 code → 400", s == 400, f"status={s}")

s, d = call("GET", f"/apps/{APP_ID}")
check("应用详情回读一致",
      s == 200 and d_(d, "code") == APP_CODE and d_(d, "name") == APP_NAME,
      f"status={s} code={d_(d, 'code')}")

# ---------------------------------------------------------------- ② 模块添加
section("② 模块添加（/api/v1/apps/{appId}/modules）")
s, d = call("POST", f"/apps/{APP_ID}/modules", {
    "name": MODULE_NAME, "code": MODULE_CODE, "description": f"{CITY}{DEPT}设备巡检工单建模",
})
check("模块创建 201", s == 201, f"status={s} {str(d)[:200]}")
MODULE_ID = d.get("data")
check("模块创建返回雪花 ID", str(MODULE_ID or "").isdigit() and int(MODULE_ID) > 0, f"data={MODULE_ID}")

s, d = call("POST", f"/apps/{APP_ID}/modules", {"code": MODULE_CODE})
check("负向：模块缺 name → 400", s == 400, f"status={s}")

s, d = call("GET", f"/apps/{APP_ID}/modules?page=1&size=50")
mods = (d.get("data") or {}).get("records") or []
hit = [m for m in mods if m.get("id") == MODULE_ID]
check("模块列表回读一致（归属 appId）",
      s == 200 and len(hit) == 1 and hit[0].get("code") == MODULE_CODE,
      f"status={s} records={len(mods)}")

# ---------------------------------------------------------------- ③ 模型设计
section("③ 模型设计（实体 + 8 字段，moduleId 归属模块）")
s, d = call("POST", "/metadata/entities", {
    "name": ENTITY_NAME, "code": ENTITY_CODE, "displayName": ENTITY_NAME,
    "tableName": TABLE_NAME, "type": 1, "deliveryMode": 1, "moduleId": MODULE_ID,
    "description": f"{CITY}{DEPT}设备巡检工单：巡检计划与执行记录",
})
check("实体创建 201", s == 201, f"status={s} {str(d)[:200]}")
ENTITY_ID = d.get("data")

s, d = call("POST", "/metadata/entities", {
    "name": ENTITY_NAME, "code": ENTITY_CODE, "displayName": ENTITY_NAME,
    "tableName": TABLE_NAME + "_x", "type": 1, "deliveryMode": 1, "moduleId": MODULE_ID,
})
check("负向：实体编码冲突 → 409 META_ENTITY_CODE_CONFLICT",
      s == 409 and ecode(d) == "META_ENTITY_CODE_CONFLICT", f"status={s} ecode={ecode(d)}")

s, d, hdrs = http.call_full("GET", f"/metadata/entities/{ENTITY_ID}")
check("实体详情：归属模块 + DRAFT 态",
      s == 200 and d_(d, "moduleId") == MODULE_ID and d_(d, "status") in (0, "DRAFT"),
      f"status={d_(d, 'status')} moduleId={d_(d, 'moduleId')}")
ent_etag = etag_of(hdrs)

for f in FIELDS:
    fb = {"name": f["code"], "code": f["code"], "displayName": f["label"], "type": f["type"]}
    if f.get("required"):
        fb["required"] = True
    if f.get("length"):
        fb["length"] = f["length"]
    if f.get("enumValues"):
        fb["enumValues"] = json.dumps(f["enumValues"], ensure_ascii=False)
    s, d = call("POST", f"/metadata/entities/{ENTITY_ID}/fields", fb)
    check(f"字段创建 {f['code']}（{f['label']}/{f['type']}）201", s == 201, f"status={s} {str(d)[:160]}")

s, d = call("POST", f"/metadata/entities/{ENTITY_ID}/fields", {"name": "x", "displayName": "缺编码字段", "type": "STRING"})
check("负向：字段缺 code → 400", s == 400, f"status={s}")

s, d = call("POST", f"/metadata/entities/{ENTITY_ID}/fields",
               {"name": "equipment_no", "code": "equipment_no", "displayName": "重复", "type": "STRING"})
check("负向：字段编码冲突 → 409 META_FIELD_CODE_CONFLICT",
      s == 409 and ecode(d) == "META_FIELD_CODE_CONFLICT", f"status={s} ecode={ecode(d)}")

s, d = call("GET", f"/metadata/entities/{ENTITY_ID}/fields?page=1&size=100")
frecs = (d.get("data") or {}).get("records") or []
by_code = {f.get("code"): f for f in frecs}
check("字段列表：8 字段全部落库", len(frecs) == 8, f"count={len(frecs)}")
check("字段类型回读正确（ENUM/DATE/TEXT/INTEGER）",
      by_code.get("equipment_type", {}).get("type") == "ENUM"
      and by_code.get("inspection_date", {}).get("type") == "DATE"
      and by_code.get("abnormal_desc", {}).get("type") == "TEXT"
      and by_code.get("duration_minutes", {}).get("type") == "INTEGER"
      and by_code.get("equipment_no", {}).get("required") in (True, 1),
      f"types={ {k: v.get('type') for k, v in by_code.items()} }")
check("ENUM 码表回读（equipment_type 3 项）",
      len(json.loads(by_code.get("equipment_type", {}).get("enumValues") or "[]")) == 3,
      f"enumValues={by_code.get('equipment_type', {}).get('enumValues')}")

s, errs, _ = validate(ENTITY_ID, kind="entity")
check("实体结构校验无 ERROR", len(errs) == 0, f"errors={errs[:2]}")

# 发布前取最新 ETag（实体版本可能被此前的写操作推进；陈旧 If-Match 会 412 假红）
s, d, hdrs = http.call_full("GET", f"/metadata/entities/{ENTITY_ID}")
ent_etag = etag_of(hdrs)

# ---------------------------------------------------------------- ④ 界面设计
section("④ 界面设计（ui-schema 草稿 → 发布闸门负向 → 正式 schema → 发布）")


def build_schema(entity_code, sections):
    return {
        "schemaVersion": 1,
        "entityCode": entity_code,
        "displayModes": ["create", "edit", "view"],
        "views": {
            "list": {
                "columns": [{"fieldCode": c, "visible": True} for c in GOOD_COLS],
                "searchFields": ["equipment_no", "equipment_name", "inspection_result"],
            },
            "form": {"sections": sections},
        },
    }


GOOD_COLS = ["equipment_no", "equipment_name", "equipment_type", "inspection_date",
             "inspector", "inspection_result"]
GOOD_SECTIONS = [
    {"id": "equip", "title": "设备信息", "controls": [
        {"fieldCode": "equipment_no"}, {"fieldCode": "equipment_name"}, {"fieldCode": "equipment_type"}]},
    {"id": "insp", "title": "巡检情况", "controls": [
        {"fieldCode": "inspection_date"}, {"fieldCode": "inspector"},
        {"fieldCode": "inspection_result"}, {"fieldCode": "duration_minutes"},
        {"fieldCode": "abnormal_desc"}]},
]
# 负向探针：表单控件引用幽灵字段（UI-REF-01 ERROR）——草稿可存（工作态），但发布必须被闸门拦下
BAD_SECTIONS = [
    GOOD_SECTIONS[0],
    {"id": "insp", "title": "巡检情况", "controls": GOOD_SECTIONS[1]["controls"] + [{"fieldCode": "ghost_field"}]},
]

s, d = put_schema(ENTITY_ID, build_schema(ENTITY_CODE, BAD_SECTIONS))
check("草稿保存（含幽灵控件引用）200 —— 草稿是工作态不拦截", s == 200, f"status={s} {str(d)[:200]}")

s, uerrs, _ = validate(ENTITY_ID)
check("界面校验：幽灵控件报 ERROR（UI-REF-01）",
      any(i.get("code", "").startswith("UI-REF") and "ghost_field" in str(i.get("message", "")) for i in uerrs),
      f"issues={uerrs[:2]}")

# 发布前取最新实体 ETag（此前的写操作可能推进版本；陈旧 If-Match 会 412 假红）
s, d, hdrs = http.call_full("GET", f"/metadata/entities/{ENTITY_ID}")
ent_etag = etag_of(hdrs)
s, d = call("POST", f"/metadata/entities/{ENTITY_ID}/publish", None, {"If-Match": ent_etag or "*"})
check("负向：ERROR 级界面问题 → 发布 409 META_PUBLISH_BLOCKED（闸门有牙）",
      s == 409 and ecode(d) == "META_PUBLISH_BLOCKED", f"status={s} ecode={ecode(d)} {str(d)[:160]}")

# 正式 schema
s, d = put_schema(ENTITY_ID, build_schema(ENTITY_CODE, GOOD_SECTIONS))
check("正式 ui-schema 草稿 PUT 200", s == 200, f"status={s} {str(d)[:200]}")

s, uerrs2, _ = validate(ENTITY_ID)
check("正式 schema 校验无 ERROR", len(uerrs2) == 0, f"errors={uerrs2[:2]}")

s, d = call("GET", f"/metadata/entities/{ENTITY_ID}/ui-schema")
saved = d_(d, "json")
if isinstance(saved, str):
    saved = json.loads(saved)
check("ui-schema 回读：6 列 / 2 分区 / 8 控件（含 INTEGER 工时）",
      saved and len(saved["views"]["list"]["columns"]) == 6
      and len(saved["views"]["form"]["sections"]) == 2
      and sum(len(x["controls"]) for x in saved["views"]["form"]["sections"]) == 8,
      f"json={str(saved)[:200]}")

s, d, hdrs = http.call_full("GET", f"/metadata/entities/{ENTITY_ID}")
ent_etag = etag_of(hdrs)
s, d = call("POST", f"/metadata/entities/{ENTITY_ID}/publish", None, {"If-Match": ent_etag or "*"})
check("实体发布 200", s == 200, f"status={s} {str(d)[:200]}")

s, d = call("GET", f"/metadata/entities/{ENTITY_ID}")
check("发布后状态 = PUBLISHED(1)", d_(d, "status") in (1, "PUBLISHED"), f"status={d_(d, 'status')}")

# ---------------------------------------------------------------- ⑤ 数据保存/查看/更新
section("⑤ 数据保存 / 查看 / 更新 / 并发保护（runtime）")


def rec(equip_no, equip_name, etype, date, inspector, result, desc="", dur=45):
    return {
        "equipment_no": equip_no, "equipment_name": equip_name, "equipment_type": etype,
        "inspection_date": date, "inspector": inspector, "inspection_result": result,
        "abnormal_desc": desc, "duration_minutes": dur,
    }


# ---- 生产仿真业务值：每个值只生成一次存变量，创建 / 断言 / MySQL 核查共用 ----
# 设备资产取 fixtures 词表（EQ-SMT-0347 式可读编号，比裸随机号可读）；
# 编号在本数据集内去重——三台撞同一编号会把「q 精确筛选恰好 1 条」顶成假红
_EQ_USED = set()


def _equip():
    """抽一台不重复的设备：(设备编号, 设备名称)。"""
    while True:
        _, _no, _name = ds.equipment()
        if _no not in _EQ_USED:
            _EQ_USED.add(_no)
            return _no, _name


EQ1_NO, EQ1_NAME = _equip()
EQ2_NO, EQ2_NAME = _equip()
EQ3_NO, EQ3_NAME = _equip()
INSPECTOR1, INSPECTOR2, INSPECTOR3 = ds.person(), ds.person(), ds.person()
INSP_DATE1 = ds.days_ago(7)   # 运行日倒推：读起来是最近发生过的巡检
INSP_DATE2 = ds.days_ago(5)
INSP_DATE3 = ds.days_ago(3)
INSP_NO_R2 = ds.doc_no("INSP")   # 巡检单号（业务前缀-日期-序号），串进异常/复检文案
INSP_NO_R3 = ds.doc_no("INSP")

R1 = rec(EQ1_NO, EQ1_NAME, "cnc", INSP_DATE1, INSPECTOR1, "normal", "", 45)
R2 = rec(EQ2_NO, EQ2_NAME, "boiler", INSP_DATE2, INSPECTOR2, "abnormal",
         f"巡检单 {INSP_NO_R2}：压力表读数偏高，需一周内复检", 60)
R3 = rec(EQ3_NO, EQ3_NAME, "conveyor", INSP_DATE3, INSPECTOR3, "recheck",
         f"巡检单 {INSP_NO_R3}：轴承润滑不足，待复检确认", 30)

s, d = call("POST", f"/runtime/entities/{ENTITY_CODE}/records", R1)
check("数据保存 R1 → 201", s == 201, f"status={s} {str(d)[:200]}")
R1_ID = d_(d, "id")
check("保存返回记录 ID", bool(R1_ID), f"data={str(d)[:160]}")

s, d = call("POST", f"/runtime/entities/{ENTITY_CODE}/records", {k: v for k, v in R1.items() if k != "equipment_no"})
check("负向：缺必填 equipment_no → 4xx", 400 <= s < 500, f"status={s} {str(d)[:160]}")

s, d = call("POST", f"/runtime/entities/{ENTITY_CODE}/records", R2)
check("数据保存 R2 → 201", s == 201, f"status={s}")
R2_ID = d_(d, "id")
s, d = call("POST", f"/runtime/entities/{ENTITY_CODE}/records", R3)
check("数据保存 R3 → 201", s == 201, f"status={s}")
R3_ID = d_(d, "id")

s, d, hdrs = http.call_full("GET", f"/runtime/entities/{ENTITY_CODE}/records/{R1_ID}")
got = d.get("data") or {}
check("数据查看：R1 回读值一致（保存→查看闭环）",
      s == 200 and got.get("equipment_name") == EQ1_NAME
      and got.get("equipment_type") == R1["equipment_type"]
      and got.get("inspection_result") == R1["inspection_result"]
      and str(got.get("inspection_date"))[:10] == INSP_DATE1,
      f"status={s} data={str(got)[:200]}")
rec_etag = etag_of(hdrs)
check("详情响应带 ETag（并发令牌）", bool(rec_etag), f"etag={rec_etag}")

# 更新：正确 ETag → 200；错误 ETag → 412
s, d = call("PUT", f"/runtime/entities/{ENTITY_CODE}/records/{R1_ID}",
               {"inspection_result": "recheck", "duration_minutes": 50,
                "inspector": INSPECTOR1},
               {"If-Match": rec_etag})
check("数据更新（正确 ETag）→ 200", s == 200, f"status={s} {str(d)[:160]}")
s, d = call("PUT", f"/runtime/entities/{ENTITY_CODE}/records/{R1_ID}",
               {"inspection_result": "normal"}, {"If-Match": '"99999"'})
check("负向：错误 ETag → 412（乐观锁有牙）", s == 412, f"status={s} {str(d)[:160]}")
s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records/{R1_ID}")
check("更新后回读：结果=recheck 工时=50（412 未污染数据）",
      (d.get("data") or {}).get("inspection_result") == "recheck"
      and (d.get("data") or {}).get("duration_minutes") in (50, "50"),
      f"data={str(d.get('data'))[:160]}")

# ---------------------------------------------------------------- ⑥ 列表展示
section("⑥ 列表展示（列投影 fields 契约 + q 精确筛选）")
proj = "equipment_no,equipment_name,inspection_date,inspector,inspection_result"
s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records?page=1&size=50&fields={proj}")
rows = (d.get("data") or {}).get("records") or []
total = (d.get("data") or {}).get("total")
check("列表：3 条记录全部可见", s == 200 and int(total) == 3 and len(rows) == 3, f"total={total} rows={len(rows)}")
row0 = rows[0] if rows else {}
check("列投影：请求的 5 列全部返回",
      all(k in row0 for k in ["equipment_no", "equipment_name", "inspection_date", "inspector", "inspection_result"]),
      f"keys={list(row0.keys())}")
check("列投影：未请求的列不下发（abnormal_desc/duration_minutes 缺席）",
      "abnormal_desc" not in row0 and "duration_minutes" not in row0,
      f"keys={list(row0.keys())}")

s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records?page=1&size=50&q=equipment_no:{EQ2_NO}")
rows_q = (d.get("data") or {}).get("records") or []
check(f"q 精确筛选：equipment_no={EQ2_NO} 恰好 1 条",
      len(rows_q) == 1 and rows_q[0].get("equipment_name") == EQ2_NAME,
      f"rows={len(rows_q)} {str(rows_q)[:120]}")

# ---------------------------------------------------------------- ⑦ 回收站（软删可观测可恢复）
section("⑦ 回收站（软删 → 可见 → 恢复）")
s, d = call("DELETE", f"/runtime/entities/{ENTITY_CODE}/records/{R3_ID}")
check("软删 R3 → 200", s == 200, f"status={s} {str(d)[:160]}")
s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records?page=1&size=50")
check("软删后用列表不见 R3", int((d.get("data") or {}).get("total") or -1) == 2,
      f"total={(d.get('data') or {}).get('total')}")
s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records/deleted?page=1&size=50")
drows = (d.get("data") or {}).get("records") or []
check("回收站可见 R3（软删不=永久删除）",
      any(str(r.get("id")) == str(R3_ID) for r in drows), f"deleted={len(drows)}")
s, d = call("POST", f"/runtime/entities/{ENTITY_CODE}/records/{R3_ID}/restore")
check("恢复 R3 → 200", s == 200, f"status={s} {str(d)[:160]}")
s, d = call("GET", f"/runtime/entities/{ENTITY_CODE}/records?page=1&size=50")
check("恢复后用列表重新可见 R3", int((d.get("data") or {}).get("total") or -1) == 3,
      f"total={(d.get('data') or {}).get('total')}")

# ---------------------------------------------------------------- ⑧ MySQL 落库核查
section("⑧ MySQL 落库核查（第二证据源）")
out, err = mysql.query(f"SELECT COUNT(*) FROM {TABLE_NAME}")
check(f"物理表 {TABLE_NAME} 存在且 3 行", out is not None and out.splitlines()[-1].strip() == "3",
      f"out={out} err={err}")
out, err = mysql.query(
    f"SELECT equipment_no, equipment_name, equipment_type, inspection_result, duration_minutes "
    f"FROM {TABLE_NAME} WHERE equipment_no='{EQ1_NO}'")
line = out.splitlines()[-1] if out else ""
check("R1 行内容与提交值一致（含更新后 recheck/50）",
      out is not None and line.startswith(EQ1_NO) and "recheck" in line and line.rstrip().endswith("50"),
      f"row={line!r} err={err}")

# ---------------------------------------------------------------- 状态文件（供 UI 探针）
state = {
    "appId": APP_ID, "appCode": APP_CODE, "appName": APP_NAME,
    "moduleId": MODULE_ID, "moduleCode": MODULE_CODE, "moduleName": MODULE_NAME,
    "entityId": ENTITY_ID, "entityCode": ENTITY_CODE, "entityName": ENTITY_NAME,
    "tableName": TABLE_NAME,
    "fields": FIELDS,
    "records": [
        {"id": R1_ID, **R1, "inspection_result": "recheck", "duration_minutes": 50},
        {"id": R2_ID, **R2},
        {"id": R3_ID, **R3},
    ],
    "suffix": SUFFIX,
}
with open(config.STATE_PATH, "w") as f:
    json.dump(state, f, ensure_ascii=False, indent=2)
note(f"状态文件 → {config.STATE_PATH}")

# ---------------------------------------------------------------- 汇总
report.finish("汇总")
