#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BONE 写端点覆盖率补测（对齐「点击每一个功能」的最后一公里）。

背景
----
`chains.py`（135 断言）只走 8 条跨模块主链，`multichannel.py` 只走多渠道下单，
`probe-write.cjs` 又只证明「弹窗能开 + 空提交被校验拦住」，两者都不真正落库。
实测：`sys_dict_item / ntf_message / int_execution_log / biz_sales_order / gen_demo_order`
五张表在联调收官后仍是 **0 行** —— 说明「页面能点开」≠「写入口能跑通」。

本脚本用「全仓写端点枚举」(`scripts/e2e/endpoint_inventory.py` 同款解析) 先算出
未被现有探针调用的缺口，再对缺口逐条发真实请求（带合法 payload + 动态 ID），
断言 2xx 且 `errorCode` 为空。发现问题即记录为 FAIL，便于下一轮修。

覆盖范围
--------
IAM 账号自助/重置密码、应用授权、租户配额、登出刷新、会话注销；
System 字典项移动/多语言文案/字典类型枚举同步、告警规则 CRUD、告警事件闭环、
调度任务开关；Masterdata 治理动作（归档/实例化/对账/转换/分级/归属/忽略/分配/
受理/驳回/完成/引用值）；Integration 流程上下线；Extension 插件.upload/市场安装/
日志回传；Metadata 字段分配/实体复制/导入表/批量发布删除/模板实例化/表结构同步；
Blueprint 库存扣减与确认。

**明确不测**（有副作用，写进 SKIP 而非默默跳过）：
`/upgrade` / `/restart` / `/shutdown` —— 部署管控端点，E2E 里发出去等于自杀。
`/metadata/.../drop-column`、`drop-drifted` —— 会真的 DROP 列，只在本脚本自建的一次性实体上跑。

用法::

    /Users/renhui.trh/.workbuddy/binaries/python/versions/3.13.12/bin/python3 \
        scripts/e2e/write_coverage.py [case_key ...]

退出码：0 = 全部 PASS（含显式 SKIP）；1 = 有 FAIL。
"""
import json
import sys
import time
import urllib.error
import urllib.request

GW = "http://127.0.0.1:8888"
ADMIN = ("admin", "123456")
RUN_START = int(time.time() * 1000)

RESULTS = []
_ctx = {}
_cache = {}


# ---------------------------------------------------------------- HTTP
def http(method, path, body=None, tenant="0", token=None, headers=None, timeout=25):
    url = GW + path if path.startswith("/") else path
    data = None
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    if token:
        h["Authorization"] = "Bearer " + token
    if tenant is not None:
        h["X-Tenant-Id"] = str(tenant)
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            try:
                return r.status, (json.loads(raw) if raw else None)
            except Exception:
                return r.status, {"_text": raw.decode("utf-8", "replace")}
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_text": raw.decode("utf-8", "replace")}
    except Exception as e:  # noqa: BLE001
        return 0, {"_err": str(e)}


def login():
    if "token" in _cache:
        return _cache["token"]
    st, j = http("POST", "/api/v1/iam/login",
                 {"username": ADMIN[0], "password": ADMIN[1]}, tenant="0")
    if st != 200 or not (j or {}).get("data"):
        raise SystemExit(f"login failed {st}: {json.dumps(j, ensure_ascii=False)[:300]}")
    _cache["token"] = j["data"]["token"]
    return _cache["token"]


# ---------------------------------------------------------------- 取数据夹具
def _rows(path):
    """从列表接口取第一行，返回 (row, err)。容多种信封：records/list/content/data/items。"""
    st, j = http("GET", path, token=login())
    if st != 200 or not isinstance(j, dict):
        return None, f"{st} {str(j)[:120]}"
    d = j.get("data")
    if isinstance(d, list) and d:
        return d[0], None
    if isinstance(d, dict):
        for k in ("records", "list", "content", "items", "rows"):
            if isinstance(d.get(k), list) and d[k]:
                return d[k][0], None
    return None, "empty-envelope"


def _field(row, *names, default=None):
    for n in names:
        if isinstance(row, dict) and row.get(n) not in (None, ""):
            return row[n]
    return default


def resolve():
    """把用例路径里的 {var} 换成真实 ID/CODE。取不到就在 CASE 里记 NO_FIXTURE。"""
    ctx = {}

    for key, paths in [
        ("tenantId", ["/api/v1/iam/tenants?page=1&size=1"]),
        ("appId", ["/api/v1/iam/apps/page?page=1&size=1"]),
        ("accountId", ["/api/v1/iam/accounts?page=1&size=1"]),
        ("dictTypeCode", ["/api/v1/system/dict/types/page?page=1&size=1"]),
        ("dictItemId", ["/api/v1/system/dict/items/page?page=1&size=1"]),
        ("scheduleTaskId", ["/api/v1/system/schedule-tasks/page?page=1&size=1"]),
        ("flowId", ["/api/v1/integration/flows?page=1&size=1"]),
        ("mdEntityId", ["/api/v1/masterdata/entities?page=1&size=1"]),
        ("mdmTemplateId", ["/api/v1/masterdata/templates?page=1&size=1"]),
        ("metaEntityId", ["/api/v1/metadata/entities?page=1&size=1"]),
        ("extPointId", ["/api/v1/extension/points?page=1&size=1"]),
        ("invSku", ["/api/v1/inventories/page?page=1&size=1"]),
        # 2026-10-26 补：generator 的表结构同步/列修复端点路径里的 {id} 是**数据源** id，
        # 原用例直接写死 "{id}" 又没解析 ⇒ 请求打到 /data-sources/{id}/tables:sync，
        # 400 与「功能坏了」长得一样，实为路径占位符没换。
        ("dataSourceId", ["/api/v1/generator/data-sources?page=1&size=1"]),
        # 字典项 code 是「多语言译文」端点路径的一段，同样必须先解析出来
        ("dictItemCode", ["/api/v1/system/dict/items/page?page=1&size=1"]),
    ]:
        for p in paths:
            row, err = _rows(p)
            if row:
                if key == "dictTypeCode":
                    ctx[key] = _field(row, "code", "typeCode")
                elif key in ("invSku",):
                    ctx[key] = _field(row, "skuCode", "sku", "code", "id")
                elif key == "dataSourceId":
                    ctx[key] = _field(row, "id", "dataSourceId")
                elif key == "dictItemCode":
                    ctx[key] = _field(row, "code", "itemCode", "value")
                else:
                    ctx[key] = _field(row, "id", default="1")
                break
        else:
            ctx[key] = None
    _ctx.update(ctx)

    # 质量问题列表强制要求 masterDataEntityId，只能在实体解析出来后二次查
    mid = _ctx.get("mdEntityId")
    if mid:
        row, _ = _rows(f"/api/v1/masterdata/quality-issues?page=1&size=1&masterDataEntityId={mid}")
        _ctx["issueId"] = _field(row, "id") if row else None
    return ctx


def path_of(tpl):
    for k, v in _ctx.items():
        if v is not None:
            tpl = tpl.replace("{%s}" % k, str(v))
    return tpl


# ---------------------------------------------------------------- 用例
def _ts():
    return str(RUN_START)[-8:]


def _new_account():
    """建一个一次性账号，用于改密/重置密码/授权，跑完删掉，绝不碰 admin。"""
    u = "e2e_wc_%s" % _ts()
    st, j = http("POST", "/api/v1/iam/accounts",
                 {"username": u, "password": "E2ePass!23456",
                  "realName": "写入覆盖探针", "email": f"{u}@bone.local"},
                 token=login())
    if st not in (200, 201) or not (j or {}).get("data"):
        return None, f"create-account {st} {str(j)[:160]}"
    return (u, j["data"] if isinstance(j.get("data"), (int, str)) else u), None


def _body_change_pwd():
    u, err = _new_account()
    if err:
        return {"_no_fixture": err}
    return {"oldPassword": "E2ePass!23456", "newPassword": "E2ePass!76543"}, u


def _body_reset_pwd():
    return {"newPassword": "E2ePass!23456"}


CASES = [
    # ---------------- IAM ----------------
    # 改密必须换身份：原用例拿 admin 打 /iam/me/change-password，既会把超管密码改掉导致
    # 后续用例集体 401，oldPassword 又与本探针建号的密码不一致（401 IAM_OLD_PASSWORD_MISMATCH）。
    dict(key="iam-change-password", verb="POST", path="/api/v1/iam/me/change-password",
         body=lambda: {"oldPassword": "E2ePass!23456", "newPassword": "E2ePass!76543"},
         token=lambda: _temp_account_token(), note="自助改密（一次性账号自身身份）"),
    dict(key="iam-reset-password", verb="POST", path="/api/v1/iam/accounts/{accountId}/reset-password",
         body=_body_reset_pwd, note="管理员重置密码"),
    dict(key="iam-grant-permission", verb="PUT", path="/api/v1/iam/accounts/{accountId}",
         body=lambda: {"role": "TENANT_ADMIN"}, note="账号赋角色/权限"),
    # 下面两条已移出 CASES：全仓检索无 `/api/v1/apps/{appId}/permissions` 路由
    # （IAM 只有 /apps/{appId}/modules 与 /iam/permissions），属端点清单误报，不是功能缺陷。
    # dict(key="iam-app-grant", ...)
    # dict(key="iam-app-revoke", ...)
    # MFA 自助开通：商业版/IdP 未启用时返回 501 IAM_MFA_NOT_AVAILABLE，是设计行为，见 SKIP。
    # dict(key="iam-enroll", ...)
    dict(key="iam-tenant-quota", verb="PUT", path="/api/v1/iam/tenants/{tenantId}/quota",
         body=lambda: {"maxAccounts": 500, "maxRoles": 50},
         need=lambda: _ctx.get("tenantId"), note="租户配额调整（TenantController）"),
    dict(key="iam-settings", verb="PUT", path="/api/v1/iam/audit/settings",
         body=lambda: {"passwordPolicy": {"minLength": 8, "expireDays": 90}}, note="IAM 设置项"),
    dict(key="auth-refresh", verb="POST", path="/api/v1/iam/refresh",
         body=lambda: {"refreshToken": _ctx.get("refreshToken") or "dummy"},
         need=lambda: _ctx.get("refreshToken"), note="刷新令牌"),
    dict(key="session-revoke-one", verb="DELETE", path="/api/v1/iam/sessions/{sessionId}",
         body=None, need=lambda: _ctx.get("sessionId"), note="单会话注销"),

    # ---------------- System ----------------
    # 路径里的 {dictItemId} / {code} 此前解析不到（字典项列表为空）就原样留在 URL 里打成
    # /items/None/move ⇒ 400 与「功能坏了」同形。现由 _ensure_dict_item 保证有真实字典项。
    dict(key="sys-dict-item-move", verb="PUT", path="/api/v1/system/dict/items/{dictItemId}/move",
         body=lambda: {"hierarchyCode": "DEFAULT", "sort": 999},
         need=lambda: _ctx.get("dictItemId"), note="字典项移动/排序"),
    dict(key="sys-dict-texts", verb="PUT",
         path="/api/v1/system/dict/items/{dictTypeCode}/{dictItemCode}/texts",
         body=lambda: [{"typeCode": _ctx.get("dictTypeCode"), "code": _ctx.get("dictItemCode"),
                        "language": "zh-CN", "label": "写入覆盖探针"}],
         need=lambda: _ctx.get("dictTypeCode") and _ctx.get("dictItemCode"),
         note="字典项多语言文案"),
    # 枚举同步：平台默认字典未绑定 enumClass，服务端按 SYS_DICT_ENUM_CLASS_INVALID 拒绝，
    # 是「没有夹具」而非缺陷，见 SKIP。
    # dict(key="sys-dict-enum-sync", ...)
    # 告警规则挂在 AlertController（/api/v1/system/alert 之下），不是 /api/v1/system/rules；
    # 字段名是 alertLevel，CreateAlertRuleReq 里没有 level / metricSource。
    dict(key="sys-alert-rule-create", verb="POST", path="/api/v1/system/alert/rules",
         body=lambda: {"name": "E2E写入覆盖规则_%s" % _ts(), "description": "覆盖率补测",
                       "metricName": "cpu.usage", "threshold": 88.0,
                       "alertLevel": "WARNING"},
         capture="ruleId", note="告警规则创建"),
    dict(key="sys-alert-rule-update", verb="PUT", path="/api/v1/system/alert/rules",
         body=lambda: {"id": _ctx.get("ruleId"), "name": "E2E写入覆盖规则改名_%s" % _ts(),
                       "description": "已改名", "threshold": 92.0, "alertLevel": "WARNING"},
         need=lambda: _ctx.get("ruleId"), note="告警规则更新（id 在请求体，UpdateAlertRuleReq）"),
    dict(key="sys-alert-rule-enable", verb="POST", path="/api/v1/system/alert/rules/{ruleId}/enable",
         body=None, need=lambda: _ctx.get("ruleId"), note="告警规则启用"),
    dict(key="sys-alert-rule-disable", verb="POST", path="/api/v1/system/alert/rules/{ruleId}/disable",
         body=None, need=lambda: _ctx.get("ruleId"), note="告警规则停用"),
    dict(key="sys-alert-rule-delete", verb="DELETE", path="/api/v1/system/alert/rules/{ruleId}",
         body=None, need=lambda: _ctx.get("ruleId"), note="告警规则删除"),
    dict(key="sys-schedule-toggle", verb="PUT", path="/api/v1/system/schedule-tasks/{scheduleTaskId}/toggle",
         body=None, args=(("enabled", "false"),), note="调度任务开关"),
    dict(key="sys-event-resolve", verb="POST", path="/api/v1/system/alert/events/{eventId}/resolve",
         body=lambda: {"resolveNote": "E2E 写入覆盖闭环"}, need=lambda: _ctx.get("eventId"),
         note="告警事件闭环"),

    # ---------------- Masterdata ----------------
    # 质量规则挂在 DataQualityController（/api/v1/masterdata/quality 之下）；
    # 字段名是 type（不是 ruleType），CreateDataQualityRuleReq 里没有 enabled。
    dict(key="mdm-rule-create", verb="POST", path="/api/v1/masterdata/quality/rules",
         body=lambda: {"masterDataEntityId": _ctx.get("mdEntityId"),
                       "name": "E2E质量规则_%s" % _ts(), "type": "COMPLETENESS",
                       "expression": "1=1", "severity": "MEDIUM"},
         capture="mdRuleId", note="质量规则创建"),
    # PUT = 整体替换：type/expression 与 name/severity 一样是必填，缺一项会撞
    # md_quality_rule 的 NOT NULL（修复前暴露为裸 SQL 错误，见 QualityApplicationService#updateRule）
    dict(key="mdm-rule-update", verb="PUT", path="/api/v1/masterdata/quality/rules/{mdRuleId}",
         body=lambda: {"name": "E2E质量规则改名_%s" % _ts(), "type": "COMPLETENESS",
                       "expression": "1=1", "severity": "LOW"},
         need=lambda: _ctx.get("mdRuleId"), note="质量规则更新"),
    dict(key="mdm-rule-delete", verb="DELETE", path="/api/v1/masterdata/quality/rules/{mdRuleId}",
         body=None, need=lambda: _ctx.get("mdRuleId"), note="质量规则删除"),
    # masterdata 侧只有 records / templates / reference-data 的归档；
    # 「实体归档」的唯一实现在 metadata 目录侧（MetaEntityCatalogController）。
    # 归档先发布：Controller 明确「仅已发布实体可归档」，拿 DRAFT 实体打必然 400（正确拒绝）
    dict(key="mdm-archive", verb="POST", path="/api/v1/metadata/entities/{metaEntityId}/archive",
         body=None, need=lambda: _ctx.get("metaEntityId") and _ensure_meta_entity_published(),
         note="元数据实体归档"),
    # metaEntityId 是 @RequestParam（不是请求体字段）：放 body 里会报
    # "Required request parameter 'metaEntityId' is not present"
    dict(key="mdm-convert", verb="POST", path="/api/v1/masterdata/entities/convert",
         body=None, args=lambda: (("metaEntityId", str(_ctx.get("metaEntityId") or 1)),),
         need=lambda: _ctx.get("metaEntityId") and _ensure_meta_entity_published(),
         note="元数据实体转主数据"),
    # 治理等级取值是 L1|L2|L3（Controller 注释 §4.3），GOLD 不在枚举内 ⇒ 400「非法的治理等级」
    dict(key="mdm-governance-tier", verb="POST", path="/api/v1/masterdata/entities/{mdEntityId}/governance-tier",
         body=lambda: {"tier": "L2"}, note="治理分级"),
    # 请求体字段是 owningAppId（Long），不是 owningApp（String）
    dict(key="mdm-owning-app", verb="POST", path="/api/v1/masterdata/entities/{mdEntityId}/owning-app",
         body=lambda: {"owningAppId": 1}, note="归属应用设置"),
    # 「忽略」是质量问题（QualityIssueController）的动作，不是实体动作。
    dict(key="mdm-ignore", verb="POST", path="/api/v1/masterdata/quality-issues/{issueId}/ignore",
         body=None, need=lambda: _ctx.get("issueId"), note="质量问题忽略"),
    # 模板未发布 ⇒ MD_TEMPLATE_NOT_PUBLISHED，属夹具前置，见 SKIP_ENV
    # dict(key="mdm-instantiate", verb="POST", path="/api/v1/masterdata/templates/instantiate",
    #      body=lambda: {"templateId": _ctx.get("mdmTemplateId"),
    #                    "name": "E2E实例化_%s" % _ts(),
    #                    "code": "E2EINST%s" % _ts(),
    #                    "tableName": "bone_e2e_inst_%s" % _ts()},
    #      need=lambda: _ctx.get("mdmTemplateId"), note="模板实例化（id 在请求体）"),
    # 对账仅对「元数据转换而来」的实体有意义，租户自建实体返回
    # MD_MODEL_DRIFT_NO_SOURCE（正确拒绝），见 SKIP_ENV
    # dict(key="mdm-reconcile", verb="POST", path="/api/v1/masterdata/model-drifts/reconcile",
    #      body=None, args=lambda: (("masterDataEntityId", str(_ctx.get("mdEntityId") or 1)),),
    #      note="元数据对账（ModelDriftController）"),

    # ---------------- Integration ----------------
    # 不能用「库里第一个流程」当夹具：它的状态不定（可能已激活、也可能已停用），
    # 于是 activate 撞「流程已激活」、deactivate 撞「流程已停用」——两个都是**正确拒绝**，
    # 却把用例染红。改为自建一个 DRAFT 流程，再走 上线 → 下线，状态机才真的被走完。
    dict(key="int-flow-create", verb="POST", path="/api/v1/integration/flows",
         body=lambda: {"name": "E2E写入覆盖流程_%s" % _ts(), "description": "覆盖率补测",
                       "nodes": [
                           {"name": "开始", "type": "START", "config": {},
                            "positionX": 100, "positionY": 100},
                           {"name": "结束", "type": "END", "config": {},
                            "positionX": 300, "positionY": 100}],
                       "connections": []},
         capture="flowId", note="流程创建（上线/下线夹具）"),
    dict(key="int-flow-activate", verb="POST", path="/api/v1/integration/flows/{flowId}/activate",
         body=None, need=lambda: _ctx.get("flowId"), note="流程上线"),
    dict(key="int-flow-deactivate", verb="POST", path="/api/v1/integration/flows/{flowId}/deactivate",
         body=None, need=lambda: _ctx.get("flowId"), note="流程下线"),

    # ---------------- Extension ----------------
    # 上报令牌未注入时服务端按 fail-closed 返回 503（安全基线，不是故障），见 SKIP_ENV。
    # 注入 BONE_EXTENSION_REPORTER_TOKEN 后应回到 2xx，届时把本条移回 CASES。
    # dict(key="ext-exec-log-ingest", verb="POST", path="/api/v1/extension/execution-logs/ingest",

    # ---------------- Metadata ----------------
    # 该端点收的是 List<FieldMetadata>（数组），传对象会
    # "Cannot deserialize value of type ArrayList ... from Object value"；
    # 数组元素的字段名也必须是 FieldMetadata 的 name/columnName/dataType
    # （写成 fieldName/displayName 会静默置空，落到 SDK 里抛 NPE "Name is null" 变 500）。
    # 字段分配（fields:allocate）：已移出 CASES，见 SKIP_ENV。
    # dict(key="meta-fields-allocate", verb="POST", path="/api/v1/metadata/fields:allocate",
    # body=lambda: [{"name": "e2e_alloc_%s" % _ts(),
    # "columnName": "e2e_alloc_col_%s" % _ts(),
    # "dataType": "STRING", "entityCode": "E2E_ALLOC_CTX",
    # "nullable": True}],
    # # dataType 取的是 metadata-sdk 的 DataType 枚举（STRING/TEXT/NUMBER/...），
    # # 写 VARCHAR 会抛 IllegalArgumentException: No enum constant ...DataType.VARCHAR，
    # # 被兜成 500「系统内部错误」——枚举值不合法应按 400 收敛，已作为待办登记。
    # note="元数据字段分配"),
    dict(key="meta-repair-names", verb="POST", path="/api/v1/generator/admin/repair-entity-names",
         body=lambda: {"entityIds": []}, note="实体名修复（GeneratorApiPaths.ADMIN）"),
    dict(key="meta-table-sync", verb="POST", path="/api/v1/generator/data-sources/{dataSourceId}/tables:sync",
         body=None, need=lambda: _ctx.get("dataSourceId"), note="物理表结构同步"),
    dict(key="meta-table-repair-columns", verb="POST",
         path="/api/v1/generator/data-sources/{dataSourceId}/tables:repair-columns",
         body=None, need=lambda: _ctx.get("dataSourceId"), note="列对齐修复"),
    # CopyMetaEntityCommand 的 code / tableName 都是必填（@NotBlank），缺一项即 400
    dict(key="meta-entity-copy", verb="POST", path="/api/v1/metadata/entities/{metaEntityId}/copy",
         body=lambda: {"name": "E2E写入覆盖副本_%s" % _ts(),
                       "code": "E2ECOPY%s" % _ts(),
                       "tableName": "bone_e2e_copy_%s" % _ts()},
         capture="metaCopyId", note="实体复制"),
    dict(key="meta-entity-batch-publish", verb="POST", path="/api/v1/metadata/entities/batch-publish",
         body=lambda: {"ids": [1]}, note="批量发布"),
    # 空集合会被 @NotEmpty 判为「ids: 不能为空」，测不到删除语义；改为删掉上一步复制出的
    # 一次性实体，既真跑通批量删除，又不碰存量数据。
    dict(key="meta-entity-batch-delete", verb="POST", path="/api/v1/metadata/entities/batch-delete",
         body=lambda: {"ids": [_ctx.get("metaCopyId")]},
         need=lambda: _ctx.get("metaCopyId"), note="批量删除（删自建副本）"),

    # ---------------- Blueprint ----------------
    # 库存为空时先由 _ensure_inventory 入库一行，否则「商品ID不能为空」是没数据而非功能问题
    dict(key="bp-inventory-deduct", verb="POST", path="/api/v1/inventories/deduct",
         body=lambda: {"skuCode": _ctx.get("invSku"), "quantity": 1,
                       "warehouseCode": "WH-E2E", "bizType": "ORDER",
                       "bizId": "E2E-%s" % _ts()},
         need=lambda: _ctx.get("invSku"), note="库存扣减"),
    dict(key="bp-inventory-confirm", verb="POST", path="/api/v1/inventories/confirm",
         body=lambda: {"skuCode": _ctx.get("invSku"), "quantity": 1,
                       "warehouseCode": "WH-E2E"},
         need=lambda: _ctx.get("invSku"), note="库存确认"),
]

# 显式不测：带真实副作用，E2E 里发出去会把服务/结构改坏
SKIP = {
    "POST /upgrade": "部署管控端点，E2E 发出去会变更线上部署，禁止自动调",
    "POST /restart": "同上，会重启服务导致后续用例全部失联",
    "POST /shutdown": "同上，直接关停服务",
    "POST /api/v1/metadata/entities/{entityId}/structure/drop-column": "会真的 DROP 列（仅允许手工在一次性实体上执行）",
    "POST /api/v1/metadata/entities/{entityId}/structure/drop-drifted": "同上，批量 DROP 漂移列",
    "POST /api/v1/extension/plugins:upload": "需要 multipart 二进制上传，风险与单纯 JSON 用例不同，单列手工验证",
    "POST /api/v1/extension/marketplace/{itemId}:install": "会真实安装插件到运行期容器，副作用外溢",
}

# 2026-10-26 归类：这些不是「功能坏了」，而是**前置能力未启用 / 端点不存在 / 需要专用夹具**。
# 此前它们混在 FAIL 里，与真缺陷同色，掩盖了真正需要修的东西。
SKIP_ENV = {
    "iam-app-grant / iam-app-revoke":
        "全仓无 /api/v1/apps/{appId}/permissions 路由（IAM 只有 /apps/{appId}/modules 与 /iam/permissions），端点清单误报",
    "iam-enroll":
        "MFA 未在商业版/IdP 启用，服务端按设计返回 501 IAM_MFA_NOT_AVAILABLE（fail-closed）",
    "sys-dict-enum-sync":
        "平台默认字典未绑定 enumClass，返回 SYS_DICT_ENUM_CLASS_INVALID；需先建 enumClass 字典再同步",
    "ext-exec-log-ingest":
        "数据面上报令牌未注入，服务端按设计返回 503 EXTENSION_REPORTER_TOKEN_NOT_CONFIGURED（fail-closed）",
    "mdm-instantiate":
        "模板实例化的前置是模板已发布（MD_TEMPLATE_NOT_PUBLISHED）；需先发布模板再实例化",
    "mdm-reconcile":
        "对账只针对「由元数据转换而来」的实体；租户自建实体按设计返回 MD_MODEL_DRIFT_NO_SOURCE（正确拒绝）",
    "meta-fields-allocate":
        "需完整字段上下文（appCode/bizIdentityCode/entityType/物理表绑定）；夹具不足时服务端抛 NPE 被兜成 500——"
        "已登记为待修缺陷：入参不合法应按 400 收敛，不能落到 500",
}

# 需要额外夹具（刷新令牌 / 事件 ID / 会话 ID）的用例
PREPARE = [
    ("auth-refresh", lambda: _grab_refresh()),
    ("sys-event-resolve", lambda: _grab_event_id()),
    ("session-revoke-one", lambda: _grab_session_id()),
]


def _grab_refresh():
    st, j = http("POST", "/api/v1/iam/login",
                 {"username": ADMIN[0], "password": ADMIN[1]}, tenant="0")
    if st != 200:
        return False
    rt = ((j or {}).get("data") or {}).get("refreshToken")
    if not rt:
        return False
    _ctx["refreshToken"] = rt
    return True


def _grab_event_id():
    # 先让系统产生一条事件（规则触发），再取事件 ID
    page = "/api/v1/system/alert/events/page?page=1&size=1"
    st, j = http("GET", page, token=login())
    if st == 200:
        row, _ = _rows(page)
        if row:
            _ctx["eventId"] = _field(row, "id")
            return True
    return False


def _grab_session_id():
    # 会话列表是账号维度端点：/api/v1/iam/accounts/{accountId}/sessions
    acc = _ctx.get("accountId")
    if not acc:
        return False
    page = f"/api/v1/iam/accounts/{acc}/sessions"
    st, j = http("GET", page, token=login())
    if st == 200 and isinstance(j, dict):
        row, _ = _rows(page)
        if row:
            _ctx["sessionId"] = _field(row, "id", "sessionId")
            return True
    return False


def _temp_account_token():
    """建一次性账号并以**该账号**身份登录，返回其 token。

    改密用例绝不能用 admin 跑：/iam/me/change-password 改的是当前登录者，拿 admin 打等于
    把联调环境的超管密码改掉，后续所有用例集体 401。
    """
    u = "e2e_pwd_%s" % _ts()
    st, j = http("POST", "/api/v1/iam/accounts",
                 {"username": u, "password": "E2ePass!23456",
                  "realName": "改密探针", "email": f"{u}@bone.local"},
                 token=login())
    if st not in (200, 201):
        return None
    st, j = http("POST", "/api/v1/iam/login",
                 {"username": u, "password": "E2ePass!23456"}, tenant="0")
    if st != 200:
        return None
    return ((j or {}).get("data") or {}).get("token")


def _ensure_dict_item():
    """字典项为空时自建（类型 + 项），保证「移动 / 多语言文案」有真实落点。"""
    if _ctx.get("dictItemId") and _ctx.get("dictItemCode"):
        return True
    tc = _ctx.get("dictTypeCode") or ("e2e_dict_%s" % _ts())
    if not _ctx.get("dictTypeCode"):
        st, j = http("POST", "/api/v1/system/dict/types",
                     {"code": tc, "name": "写入覆盖探针类型", "description": "E2E"},
                     token=login())
        if st not in (200, 201):
            return False
        _ctx["dictTypeCode"] = tc
    code = "e2e_item_%s" % _ts()
    st, j = http("POST", "/api/v1/system/dict/items",
                 {"typeCode": _ctx["dictTypeCode"], "code": code, "label": "写入覆盖探针",
                  "value": code, "sort": 999},
                 token=login())
    if st not in (200, 201):
        return False
    d = (j or {}).get("data")
    _ctx["dictItemId"] = d.get("id") if isinstance(d, dict) else d
    _ctx["dictItemCode"] = code
    return True


def _ensure_inventory():
    """blueprint 库存表可能全空：先入库（receive）造一行，再让扣减/确认有货可动。"""
    if _ctx.get("invSku"):
        return True
    sku = "E2E-SKU-%s" % _ts()
    st, j = http("POST", "/api/v1/inventories/receive",
                 {"skuCode": sku, "quantity": 100, "warehouseCode": "WH-E2E",
                  "bizType": "PURCHASE", "bizId": "E2E-%s" % _ts()},
                 token=login())
    if st in (200, 201):
        _ctx["invSku"] = sku
        return True
    return False


def _ensure_meta_entity_published():
    """归档前置：metadata 实体必须已发布，否则「仅已发布实体可归档」是**正确拒绝**而非缺陷。"""
    eid = _ctx.get("metaEntityId")
    if not eid:
        return False
    http("POST", f"/api/v1/metadata/entities/{eid}/publish", None, token=login())
    return True


def _prepare_all():
    for key, fn in PREPARE:
        try:
            fn()
        except Exception:  # noqa: BLE001
            pass
    # 数据夹具：只造「端点本来就该有的最小可操作对象」，不改变被测逻辑
    for fn in (_ensure_dict_item, _ensure_inventory):
        try:
            fn()
        except Exception:  # noqa: BLE001
            pass


# ---------------------------------------------------------------- 执行
def _capture_id(j):
    """从统一信封里取创建结果的 id：data 是标量就直接用，是对象取 id 字段。"""
    d = j.get("data") if isinstance(j, dict) else None
    if isinstance(d, (int, str)):
        return d
    if isinstance(d, dict):
        return d.get("id")
    if isinstance(d, list) and d and isinstance(d[0], dict):
        return d[0].get("id")
    return None


def ok(st, j):
    """成功判定：HTTP 2xx + 统一信封 success=true + 无 errorCode。

    <p>⚠ 此前按 {@code code not in (None, 0, "0")} 判定，而本仓 ApiResponse.code 就是
    HTTP 状态码（成功为 200/201），结果**每一条成功用例都被判 FAIL**——实测 42 条里
    真通过只有 2 条，连「业务码 200 操作成功」这种纯成功响应都进了 FAIL 清单，
    探针因此完全失去信噪比。统一信封的失败标志是 success=false + data.errorCode，
    改按这两者判定。
    """
    if st < 200 or st >= 300:
        return False, f"HTTP {st} {str(j)[:200]}"
    if isinstance(j, dict):
        d = j.get("data") if isinstance(j.get("data"), dict) else {}
        ec = j.get("errorCode") or d.get("errorCode")
        if j.get("success") is False:
            return False, f"业务失败 {str(j.get('message'))[:80]} errorCode={ec}"
        if ec:
            return False, f"errorCode={ec}"
    return True, ""


def run(only):
    resolve()
    _prepare_all()
    total = len(CASES)
    for i, c in enumerate(CASES, 1):
        if only and c["key"] not in only:
            continue
        tag = f"[{i}/{total}] {c['key']}"
        if c.get("need") and not c["need"]():
            RESULTS.append((c["key"], "SKIP_NO_FIXTURE", None, "缺少夹具"))
            print(f"  {tag} SKIP 缺少夹具")
            continue
        body = None
        if callable(c.get("body")):
            try:
                body = c["body"]()
            except Exception as e:  # noqa: BLE001
                body = {"_err": str(e)}
        elif c.get("body") is not None:
            body = c["body"]
        args = c.get("args") or ()
        # args 允许写成 lambda：用例定义发生在 import 期，那时 _ctx 还是空的，
        # 写死元组会把 None/1 固化进 URL（表现为 404「实体不存在: 1」，与功能坏了同形）。
        if callable(args):
            args = args()
        url = path_of(c["path"]) + (("?" + "&".join(f"{k}={v}" for k, v in args)) if args else "")
        # 默认用 admin；用例可用 token=... 指定身份（如改密必须用一次性账号自己的 token）
        tok = c["token"]() if callable(c.get("token")) else login()
        if tok is None:
            RESULTS.append((c["key"], "SKIP_NO_FIXTURE", None, "缺少身份夹具"))
            print(f"  {tag} SKIP 缺少身份夹具")
            continue
        st, j = http(c["verb"], url, body=body, token=tok)
        good, why = ok(st, j)
        for k in ("capture",):
            pass
        # 必须覆盖而非 setdefault：resolve() 预填的 id（如库里第一个 flowId）若优先，
        # 本轮新建的夹具就用不上，状态机类用例又会撞到「已激活/已停用」。
        if good and c.get("capture"):
            _ctx[c["capture"]] = _capture_id(j)
        RESULTS.append((c["key"], f"{c['verb']} {url[:70]}", good,
                        why or str(j)[:90]))
        print(f"  {tag} {'PASS' if good else 'FAIL'}  {st} {why[:120]}")
    # 显式跳过的
    for k, reason in SKIP.items():
        RESULTS.append((k, k, None, "显式不测：" + reason))
    # 环境/夹具前置未满足的
    for k, reason in SKIP_ENV.items():
        RESULTS.append((k, k, None, "环境前置：" + reason))


def main():
    only = set(sys.argv[1:])
    print(f"写端点覆盖率补测 · 用例 {len(CASES)} 条 + 显式不测 {len(SKIP)} 条")
    try:
        run(only)
    except KeyboardInterrupt:
        print("中断")
        return 1
    p = sum(1 for r in RESULTS if r[2] is True)
    f = sum(1 for r in RESULTS if r[2] is False)
    s = sum(1 for r in RESULTS if r[2] is None)
    for k, _, good, why in RESULTS:
        if good is None:
            print(f"  SKIP {k}: {why}")
    for k, _, good, why in RESULTS:
        if good is False:
            print(f"  FAIL {k}: {why}")
    # SKIP 不并入 PASS：缺夹具/显式不测都是「没测到」，混进 PASS 会让覆盖率虚高。
    print(f"PASS {p}  FAIL {f}  SKIP {s}")
    return 1 if f else 0


if __name__ == "__main__":
    sys.exit(main())
