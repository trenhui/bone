# -*- coding: utf-8 -*-
"""建模侧（catalog）公共助手：实体 / 字段 / 子表字段 / 动作定义 / 界面协议 / 发布。

统一口径（三份全链路脚本合并前的差异已收敛到此）：

  * 所有函数返回 `(status, body)` 二元组，断言自己判状态码；
  * `create_entity` 幂等：编码冲突（409 META_ENTITY_CODE_CONFLICT）时复用既有实体，
    并做**物理表纠偏**（历史实体绑错物理表 ⇒ 子表写入撞列静默失败，见函数内注释）；
  * `put_schema` 兼容两种入参：样例文件名（str）或 PUT 体（dict），锁策略 GET-first + 412 重试；
  * `validate` 分两种：`kind="ui"`（界面协议校验，POST）与 `kind="entity"`（实体结构校验，GET），
    统一返回 `(status, errors, all_issues)`。
"""
import json
import os

from . import config, http
from .report import note


# ---------------------------------------------------------------- 实体
def find_entity_id_by_code(code):
    """全量翻页扫描后本地精确匹配 code。

    曾经写死单页 size ⇒ 实体数超过容量后老实体落在后续页之外，
    「编码冲突」分支定位不到 id，脚本假失败（探针缺陷，非产品缺陷）。
    """
    page, size = 1, 100
    while page <= 50:
        s, d = http.call("GET", f"/metadata/entities?page={page}&size={size}")
        recs = (d.get("data") or {}).get("records") or []
        for r in recs:
            if r.get("code") == code:
                return r.get("id")
        if len(recs) < size:
            return None
        page += 1
    return None


def create_entity(name, code, table, row_policies=None, fields=(), auto_number=None,
                  delivery=1, module_id=None, description="E2E 样板场景"):
    """幂等创建实体：已存在则复用并纠偏物理表，随后补齐字段。返回实体 id。"""
    body = {
        "name": name,
        "code": code,
        "displayName": name,
        "tableName": table,
        "type": 1,
        "deliveryMode": delivery,
        "description": description,
    }
    if module_id is not None:
        body["moduleId"] = module_id
    if row_policies:
        body["rowPolicies"] = json.dumps(row_policies, ensure_ascii=False)
    if auto_number:
        body["autoNumber"] = json.dumps(auto_number, ensure_ascii=False)
    s, d = http.call("POST", "/metadata/entities", body)
    if s in (200, 201):
        eid = d.get("data")
    elif http.ecode(d) == "META_ENTITY_CODE_CONFLICT":
        # 幂等复用：编码已存在（上一轮 E2E 或既有种子建的实体）——重复建模不该撞车
        eid = find_entity_id_by_code(code)
        note(f"[复用] 实体 {code} 已存在 id={eid}")
        assert eid, f"实体 {code} 冲突且无法定位"
        # 物理表纠偏（2026-10-26 全模块联调）：历史实体可能绑着别人的物理表 —— 典型是 claim_item
        # 绑到了 bone-init.sql（DDL 真源）里 synap-claim 模块的 t_claim_item：该表列是
        # item_name/quantity/unit_price…，明细模型却是 name/amount/sort_order ⇒ 子表写入撞列
        # 静默失败、明细恒空，submit 永远被 SUB-CONS-01（发票≠清单合计）拦住。表名不一致时先
        # 回草稿改 tableName（revert-draft 不删旧物理表），随后的 publish 按新表名建表对齐。
        s0, d0 = http.call("GET", f"/metadata/entities/{eid}")
        cur_table = ((d0 or {}).get("data") or {}).get("tableName")
        if cur_table and cur_table != table:
            s1, d1 = http.call("POST", f"/metadata/entities/{eid}/revert-draft")
            assert s1 in (200, 204), f"revert-draft {s1} {str(d1)[:200]}"
            up = {k: v for k, v in body.items() if k != "moduleId"}
            s2, d2 = http.call("PUT", f"/metadata/entities/{eid}", up)
            assert s2 in (200, 204), f"rebind tableName {s2} {str(d2)[:200]}"
            note(f"[纠偏] {code} 物理表 {cur_table} → {table}")
    else:
        raise AssertionError(f"create entity {code} failed {s} {str(d)[:200]}")
    for f in fields:
        ensure_field(eid, f)
    return eid


# ---------------------------------------------------------------- 字段
def ensure_field(eid, f):
    """字段幂等创建：已存在（409/400 code 冲突）视为已具备，返回状态码。"""
    fb = {"name": f["code"], "code": f["code"], "displayName": f["label"],
          "type": f.get("type", "STRING")}
    for k in ("required", "length", "precision"):
        if f.get(k) is not None:
            fb[k] = f[k]
    if f.get("enumValues"):
        fb["enumValues"] = json.dumps(f["enumValues"], ensure_ascii=False)
    if f.get("validationRules"):
        fb["validationRules"] = json.dumps(f["validationRules"], ensure_ascii=False)
    s, d = http.call("POST", f"/metadata/entities/{eid}/fields", fb)
    if s not in (200, 201):
        note(f"[skip] 字段 {f['code']} 已存在或创建失败（{s}）")
    return s


def child_table_field(entity_id, field_code, child_entity_code=None, fk=None,
                      physical=False, type="LONG"):
    """子表/外键字段声明（引擎方言：CHILD_TABLE 类型字段 + validation_rules.childTable）。

    `physical=True` 声明的是子表上的**物理外键列**（LONG/INT），不带 childTable 规则。
    """
    if physical:
        fb = {"name": field_code, "code": field_code, "displayName": field_code, "type": type}
    else:
        fb = {
            "name": field_code,
            "code": field_code,
            "displayName": field_code,
            "type": "CHILD_TABLE",
            "validationRules": json.dumps(
                {
                    "childTable": {
                        "childEntityCode": child_entity_code,
                        "foreignKey": fk,
                        "orderColumn": "sort_order",
                        "cascadeDelete": True,
                        "minRows": 0,
                        "maxRows": 200,
                    }
                },
                ensure_ascii=False,
            ),
        }
    s, d = http.call("POST", f"/metadata/entities/{entity_id}/fields", fb)
    return s


# 别名：ui-schema-samples-e2e.py 的历史命名，保留以免改调用点
add_child_table_field = child_table_field


def add_action(eid, code, label, frm, to, escape=False, handler=None, idem=False,
               order=0, status_field="status"):
    """动作定义建模（POST /action-defs），返回 `(status, body)`。"""
    body = {
        "actionCode": code,
        "label": label,
        "fromStatus": frm,
        "toStatus": to,
        "statusField": status_field,
        "escape": escape,
        "requiresIdempotency": idem,
        "sortOrder": order,
    }
    if handler:
        body["handlerBean"] = handler
    return http.call("POST", f"/metadata/entities/{eid}/action-defs", body)


# ---------------------------------------------------------------- 界面协议
def load_sample(sample):
    """读样例协议文件原文（真源：doc/design/lowcode/cases/ui-schema-samples）。"""
    with open(os.path.join(config.SAMPLES, sample)) as fh:
        return fh.read()


def put_schema(eid, schema):
    """PUT 界面协议，返回 `(status, body)`。

    `schema` 传样例文件名（str，自动读原文包成 `{"json": ...}`）或 PUT 体（dict）。
    锁口径：GET 当前 lockVersion（无草稿行用 -1）→ PUT；412 竞态时取最新锁重试一次。
    """
    if isinstance(schema, str):
        schema = {"json": load_sample(schema)}
    lock = _schema_lock(eid)
    s, d = http.call("PUT", f"/metadata/entities/{eid}/ui-schema", schema, {"If-Match": lock})
    if s == 412:
        lock = _schema_lock(eid)
        s, d = http.call("PUT", f"/metadata/entities/{eid}/ui-schema", schema, {"If-Match": lock})
    return s, d


def _schema_lock(eid):
    s, d = http.call("GET", f"/metadata/entities/{eid}/ui-schema")
    lock = ((d or {}).get("data") or {}).get("lockVersion")
    return str(lock) if lock is not None else "-1"


def validate(eid, kind="ui"):
    """校验，返回 `(status, errors, all_issues)`。

    kind="ui"    → POST /ui-schema/validate（界面协议引用完整性，publish 闸门同源）
    kind="entity"→ GET /entities/{id}/validate（实体结构校验）
    """
    if kind == "entity":
        s, d = http.call("GET", f"/metadata/entities/{eid}/validate")
    else:
        s, d = http.call("POST", f"/metadata/entities/{eid}/ui-schema/validate")
    issues = ((d or {}).get("data") or []) if isinstance(d, dict) else []
    if not isinstance(issues, list):
        issues = []
    return s, [i for i in issues if i.get("level") == "ERROR"], issues


def publish(eid):
    """发布实体，返回 `(status, body)`；调用方按业务判 200/409。"""
    return http.call("POST", f"/metadata/entities/{eid}/publish")
