# -*- coding: utf-8 -*-
"""链路「定义数据 + 通用执行器」中的执行器：链路只声明步骤与判据。

背景（README §8「仍开放」表第 4 行）
----------------------------------
``chains.py`` 等跨模块链路脚本原先是单体大文件：每一步都是同一套样板
「发请求 → 落断言 → 从响应取变量 → 传给下一步」，新增一条链路就得往大文件里
再抄一遍，易冲突、断言口径也容易漂移。本模块把这份同构样板收敛为通用执行器
``run_steps``；链路侧只剩**数据**（步骤字典列表）与少量特殊逻辑钩子。

步骤字典（字段全可选，缺省即最常见形态）::

    {
        "method": "POST",                  # HTTP 方法；省略 = 不发请求的纯断言步骤
        "path": "/api/v1/orders/{oid}",    # 路径模板，{名} 由上下文变量填充
        "body": {...} | callable(ctx),     # 请求体；省略 = 无请求体（body=None）
        "token": <token> | None | callable(ctx),  # 省略 = 主身份；None = 匿名回调
        "call": callable(ctx) -> (st, j),  # 自定义取数（轮询等特殊传输），替代 method/path
        "when": callable(ctx) -> bool,     # 前置条件；False 跳过整步（不发请求）
        "checks": [判据, ...],             # 落断言；省略 = 只取数不断言
        "extract": {"变量": 提取器},        # 响应取值写入 ctx，供后续步骤引用
        "stop_if_empty": ["变量"],         # 变量为空即结束本链（等价原来的 if not x: return）
        "post": callable(ctx, st, j),      # 特殊逻辑钩子（断言之后执行）
    }

单步执行顺序：**执行 → 提取变量 → 落断言 → 空值止损 → post 钩子**。
提取放在断言之前，是因为不少判据/详情要引用本步刚取到的变量（如 ``ctx.amount``）；
``stop_if_empty`` 放在断言之后，才与原来「先断言再 return」的口径一致。

判据条目按 ``kind`` 分派到既有断言函数，语义与逐行手写完全一致::

    {"name": ..., "kind": "status", "expect": 200, "detail": ...}    # 期望状态码
    {"name": ..., "kind": "eq", "expect": "PENDING_APPROVAL",
     "actual": "data.status" | callable, "detail": ...}              # 期望响应字段值
    {"name": ..., "kind": "cond", "actual": callable(ctx, st, j), "detail": ...}  # 任意谓词
    {"name": ..., "kind": "err", "status": 409, "code": "BP_..."}    # 业务码落位 check_err
    {"name": ..., "kind": "page", "expect_rows": 3, "required": (...),
     "min_rows": 1, "save_as": "rows"}                               # 分页信封 check_page_shape
    {"name": ..., "kind": "list", "required": (...), "min_rows": 1, "save_as": "rows"}
                                                                     # 数组形状 check_list_shape

条目可带 ``when``（条件不成立则只跳过这一条断言）；``detail`` 可为字面串或
``callable(ctx, st, j)``；``expect``/``actual`` 为字符串时按点号路径取响应值。

特殊逻辑（夹具准备、登录、文件门禁…）不硬塞进展骤字段：直接把一个
``callable(ctx)``（或一行分节标题字符串）放进步骤列表即可——不为抽象而抽象。
"""
import re


# ==================== 断言与结果（原 chains.py 内联，抽出后各链共用一份） ====================

RESULTS = []


def judge(expect, actual):
    """判定 expect/actual 是否命中：可调用谓词 > 元组任一命中 > 相等。

    ``check`` 与外部脚本注入的 ``emit`` 出口（如 multichannel 走 report.check）
    共用这一份判定语义，避免各脚本自造轮子导致判据漂移。
    """
    if callable(expect):
        return expect(actual)
    if isinstance(expect, tuple):
        return actual in expect
    return expect == actual


def check(cid, name, expect, actual, detail=""):
    """落一条断言并记入 ``RESULTS``；expect 可为可调用谓词或元组（任一命中）。"""
    ok = judge(expect, actual)
    mark = "PASS" if ok else "FAIL"
    RESULTS.append((cid, name, ok, f"expect={expect} actual={actual}"))
    tail = f"  {detail}" if detail else ""
    print(f"  [{mark}] {name}: expect={expect} actual={actual}{tail}")
    return ok


def data(j):
    return j.get("data") if isinstance(j, dict) else None


def rid_of(j):
    """写接口可能直接返回裸 ID 字符串，也可能返回对象，统一收敛。"""
    v = data(j)
    if isinstance(v, str):
        return v
    if isinstance(v, dict):
        return v.get("id")
    return None


def pick(j, path):
    """按点号路径取响应值，等价原链路手写的 ``(data(j) or {}).get(k)`` 逐级写法。"""
    cur = j
    for k in path.split("."):
        cur = cur.get(k) if isinstance(cur, dict) else None
    return cur


# ---- 结构化断言工具（替代「只看 200」的弱断言） ----
#
# 为什么需要：仅断言 HTTP 200 会把「返回了正确形状的空壳」判为通过。此前实测到两个
# 被 200 断言完全掩盖的真实缺陷：
#   1) /api/v1/iam/audit/logs 传 pageNum/pageSize 被静默忽略（后端字段是 page/size），
#      永远返回第 1 页 10 条 —— 翻页功能在测试里「通过」但线上失效。
#   2) 多个列表端点返回体是 list 还是 PageResult 不一致，弱断言无法区分「真的空」与
#      「解析姿势错了拿到空」。
# 下面各函数分别锁定：信封形状、元素形状+必填字段、分页参数真的生效。

def page_rows(j):
    """从 PageResult 里取当前页行；兼容 list/records/content 三种键 + 裸 list。"""
    d = data(j)
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        for k in ("list", "records", "content", "items"):
            if isinstance(d.get(k), list):
                return d[k]
    return None


def check_page_shape(cid, name, j, expect_rows=None, required=(), min_rows=0):
    """断言分页信封结构正确。

    expect_rows: 期望的 pageSize。实际行数应为 min(pageSize, 该页剩余条数)，
    故断言「恰好等于」在 pageSize 大于剩余时会误判 —— 这里改为双向夹逼：
    行数不得超过 pageSize，且当剩余足够时必须恰好等于 pageSize
    （后者才是「pageSize 真生效」的证据；前者只证明它没超发）。
    required: 每行必填字段元组，任一缺失即失败。
    """
    d = data(j)
    if not isinstance(d, dict):
        check(cid, f"{name}·分页信封", "PageResult 对象", type(d).__name__, str(j)[:120])
        return None
    missing_meta = [k for k in ("total", "page", "size") if k not in d]
    check(cid, f"{name}·分页信封(total/page/size 齐备)", [], missing_meta, f"keys={sorted(d.keys())[:8]}")
    rows = page_rows(j)
    if rows is None:
        check(cid, f"{name}·行集合存在", "list", "缺失", f"keys={sorted(d.keys())[:8]}")
        return None
    check(cid, f"{name}·行集合非空且≥{min_rows}", True, isinstance(rows, list) and len(rows) >= min_rows,
          f"rows={len(rows) if isinstance(rows, list) else type(rows).__name__}")
    if expect_rows is not None and isinstance(rows, list):
        total = d.get("total")
        n = len(rows)
        check(cid, f"{name}·行数不超过 pageSize({expect_rows})", True, n <= expect_rows,
              f"rows={n} total={total}")
        # 剩余条数足够时（total >= pageSize）必须恰好取满，否则说明 pageSize 被忽略
        if isinstance(total, int) and total >= expect_rows:
            check(cid, f"{name}·pageSize 真生效(应取满 {expect_rows} 行)", expect_rows, n,
                  f"total={total} page={d.get('page')} size={d.get('size')}")
    if required and isinstance(rows, list) and rows:
        sample = rows[0] if isinstance(rows[0], dict) else {}
        absent = [f for f in required if f not in sample]
        check(cid, f"{name}·首行必填字段 {list(required)}", [], absent, f"首行键={sorted(sample.keys())[:8]}")
    return rows


def check_list_shape(cid, name, j, required=(), min_rows=0):
    """断言裸 list 端点：确实是数组，且元素必填字段齐备。"""
    rows = data(j)
    if not isinstance(rows, list):
        check(cid, f"{name}·返回数组", "list", type(rows).__name__, str(j)[:120])
        return None
    check(cid, f"{name}·非空且≥{min_rows}", True, len(rows) >= min_rows, f"count={len(rows)}")
    if required and rows:
        sample = rows[0] if isinstance(rows[0], dict) else {}
        absent = [f for f in required if f not in sample]
        check(cid, f"{name}·首行必填字段 {list(required)}", [], absent, f"首行键={sorted(sample.keys())[:8]}")
    return rows


def check_err(cid, name, st, j, want_status, want_code=None, strict_code=True):
    """断言错误响应：状态码 + 业务错误码必须落在 ``data.errorCode``。

    历史（2026-10-02 第三轮修正）：
        本函数原先是「errorCode 或 message 前缀任一命中即过」。这是**弱断言** ——
        业务码只落在 message 前缀、``data.errorCode`` 为 null 时它照样 PASS，
        于是 2026-10-02 实测的缺陷（masterdata 全部 ``MD_*`` 码 errorCode 为 null）
        长期测不出来，直到人工比对响应才发现。

        根因已修（``MasterDataErrors.of`` 曾误用 3 参 ``BizException`` 构造器，
        该构造器把 errorCode 恒置 null；HTTP 状态仍正确，故弱断言测不出）。
        故本函数改为**强制 errorCode 落位**：want_code 命中不了 errorCode 就 FAIL。

    ``strict_code=False`` 仅用于已确认无法结构化的历史路径（会打印实际落点）。
    """
    d = data(j)
    code = d.get("errorCode") if isinstance(d, dict) else None
    msg = str((j or {}).get("message") or "")
    ok = st == want_status
    where = "不校验"
    if want_code:
        if code == want_code:
            where = "data.errorCode"
            ok = ok and True
        elif not strict_code and want_code in msg:
            where = "message前缀(errorCode为null，已降级)"
            ok = ok and True
        else:
            where = f"未落位(实际errorCode={code!r})"
            ok = False
    else:
        ok = ok and bool(code)
    check(cid, name, True, ok, f"HTTP={st} 期望={want_status} 码落点={where} msg={msg[:70]}")
    return code


def d_body(n=160):
    """断言详情生成器：响应体片段（替代各处手写的 ``str(j)[:n]``）。"""
    return lambda ctx, st, j: str(j)[:n]


# ==================== 通用执行器 ====================

_PATH_VAR = re.compile(r"\{(\w+)\}")


class ChainCtx:
    """一条链路的运行期上下文。

    ``http``  请求入口，签名 ``(method, path, body, token) -> (status, body)``；
    ``main``  缺省身份 token（步骤省略 ``token`` 时用它）；
    ``cid``   断言归属的链路号（如 ``"C1"``）。
    ``extract`` / 钩子写入的变量直接挂成属性（``ctx.rid`` 等），
    变量名不得与 ``http`` / ``main`` / ``cid`` 冲突。
    """

    def __init__(self, http, main=None, cid=""):
        self.http = http
        self.main = main
        self.cid = cid
        # 命中 stop_if_empty 时置位：链路方据此决定是否还要返回收尾值
        self.stopped = False


def run_steps(steps, ctx, emit=None):
    """通用执行器：按声明顺序「执行一步 → 落断言 → 提取变量 → 传给下一步」。

    ``emit(name, expect, actual, detail)`` 是落断言的出口，缺省走本模块的
    ``check(ctx.cid, ...)``（chains 风格）；非 chains 脚本可注入自己的出口
    （如 multichannel 仍走 ``e2e_lib.report.check``，保持原有输出口径）。
    """
    if emit is None:
        def emit(name, expect, actual, detail=""):
            return check(ctx.cid, name, expect, actual, detail)
    for step in steps:
        if isinstance(step, str):
            print(step)                      # 分节标题
            continue
        if callable(step):                   # 非请求型步骤（夹具准备 / 登录 / 文件门禁）
            step(ctx)
            continue
        if "when" in step and not step["when"](ctx):
            continue
        st, j = _request(step, ctx)
        # 1. 提取变量：先取数，供本步判据与后续步骤共用（同一只生成一次）
        for key, fn in (step.get("extract") or {}).items():
            setattr(ctx, key, fn(ctx, st, j) if callable(fn) else pick(j, fn))
        # 2. 落断言
        for spec in step.get("checks") or ():
            _judge(spec, ctx, st, j, emit)
        # 3. 空值止损：等价原来「取不到 id 就 return」的中断语义
        for key in step.get("stop_if_empty") or ():
            if not getattr(ctx, key):
                ctx.stopped = True
                return
        # 4. 特殊逻辑钩子
        if "post" in step:
            step["post"](ctx, st, j)
    return ctx


def _request(step, ctx):
    """执行一步的取数：自定义 ``call`` > 普通 HTTP > 纯断言（不发请求）。"""
    if "call" in step:
        return step["call"](ctx)
    if "method" not in step:
        return None, None
    body = step.get("body")
    if callable(body):
        body = body(ctx)
    token = ctx.main
    if "token" in step:
        token = step["token"]
        if callable(token):
            token = token(ctx)
    path = _PATH_VAR.sub(lambda m: str(getattr(ctx, m.group(1))), step.get("path", ""))
    return ctx.http(step["method"], path, body, token)


def _judge(spec, ctx, st, j, emit):
    """把一条判据分派到对应断言语义。"""
    if "when" in spec and not spec["when"](ctx):
        return
    name = spec["name"]
    detail = spec.get("detail")
    if callable(detail):
        detail = detail(ctx, st, j)
    detail = detail or ""
    kind = spec.get("kind", "cond")
    if kind == "status":
        emit(name, spec["expect"], st, detail)
    elif kind in ("eq", "cond"):
        expect = spec["expect"](ctx) if callable(spec.get("expect")) else spec.get("expect", True)
        actual = spec["actual"]
        if callable(actual):
            actual = actual(ctx, st, j)
        elif kind == "eq":
            actual = pick(j, actual)     # 字符串 = 响应取值路径（如 "data.status"）
        emit(name, expect, actual, detail)
    elif kind == "err":
        check_err(ctx.cid, name, st, j, spec["status"], spec.get("code"), spec.get("strict", True))
    elif kind == "page":
        rows = check_page_shape(ctx.cid, name, j, spec.get("expect_rows"),
                                spec.get("required") or (), spec.get("min_rows", 0))
        if "save_as" in spec:
            setattr(ctx, spec["save_as"], rows)
    elif kind == "list":
        rows = check_list_shape(ctx.cid, name, j, spec.get("required") or (), spec.get("min_rows", 0))
        if "save_as" in spec:
            setattr(ctx, spec["save_as"], rows)
    else:
        raise ValueError(f"未知判据 kind={kind}（步骤 {name}）")
