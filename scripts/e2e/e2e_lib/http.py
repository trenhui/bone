# -*- coding: utf-8 -*-
"""HTTP 客户端与会话（登录、多身份、响应解析）——全部 E2E 脚本的请求唯一入口。

约定：
  * `call()` 返回 `(status, body)` —— 断言只关心状态码与报文，90% 的用例够用；
  * `call_full()` 返回 `(status, body, headers)` —— 需要 `ETag` / 响应头时用它；
  * `call_as(hdr, ...)` 用**另一个身份**的头发起请求（职责分离 SoD 场景必需）；
  * 网络异常不抛栈：返回 `(0, {"error": ...})`，让断言自己判红，避免一个超时毁掉整轮覆盖。

典型用法::

    from e2e_lib import http, report

    http.login()                     # 主会话（admin），失败直接 sys.exit
    s, d = http.call("GET", "/apps") # 主会话请求
    report.check("应用列表可读", s == 200, f"{s}")
"""
import base64
import json
import sys
import urllib.error
import urllib.request

from . import config


# ---------------------------------------------------------------- 底层
def raw_call(base, method, path, body=None, headers=None, timeout=None):
    """发一次请求，返回 `(status, body, headers)`；网络异常返回 `(0, {...}, {})`。"""
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout or config.TIMEOUT) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else {}), dict(r.headers)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt), dict(e.headers)
        except Exception:
            return e.code, {"raw": txt[:400]}, dict(e.headers)
    except Exception as e:  # 连接拒绝/超时：让断言判红，不中断整轮
        return 0, {"error": f"{type(e).__name__}: {e}"}, {}


# ---------------------------------------------------------------- 会话
class Session:
    """一份凭据对应一个会话；一个脚本可持多个会话（主操作人 / 审批人 / 渠道回调）。"""

    def __init__(self, user=None, pwd=None, tenant=None, base=None, login_base=None):
        self.user = user if user is not None else config.USER
        self.pwd = pwd if pwd is not None else config.PWD
        self.tenant = tenant if tenant is not None else config.TENANT
        self.base = base or config.BASE
        # 登录地址与 API 地址解耦（直连后端时登录走 IAM，见 config.LOGIN_BASE）
        self.login_base = login_base or config.LOGIN_BASE
        self.token = None

    # ---- 认证 ----
    def login(self, fatal=True):
        """登录并保存 token；`fatal=True` 时登录失败直接退出（脚本没登录跑什么都是假红）。"""
        s, d, _ = raw_call(
            self.login_base, "POST", "/iam/login",
            {"username": self.user, "password": self.pwd},
            {"X-Tenant-Id": self.tenant},
        )
        tok = (d.get("data") or {}).get("token") if s == 200 else None
        if not tok:
            if fatal:
                sys.exit(f"登录失败 {self.user}@{self.login_base} -> {s} {str(d)[:200]}")
            return None
        self.token = tok
        return tok

    def headers(self, extra=None):
        h = {
            "Authorization": "Bearer " + (self.token or ""),
            "X-Tenant-Id": self.tenant,
            "Content-Type": "application/json",
        }
        if extra:
            h.update(extra)
        return h

    # ---- 请求 ----
    def request(self, method, path, body=None, extra=None):
        """主会话请求，返回 `(status, body, headers)`。"""
        return raw_call(self.base, method, path, body, self.headers(extra))

    def call(self, method, path, body=None, extra=None):
        """主会话请求，返回 `(status, body)`。"""
        s, d, _ = self.request(method, path, body, extra)
        return s, d


_DEFAULT = None


def default():
    """默认（主）会话；首次调用自动登录。"""
    global _DEFAULT
    if _DEFAULT is None:
        _DEFAULT = Session()
        _DEFAULT.login()
    return _DEFAULT


def set_default(session):
    """显式指定默认会话（脚本自建会话时用）。"""
    global _DEFAULT
    _DEFAULT = session
    return session


def login(user=None, pwd=None, fatal=True):
    """登录并把该会话设为默认；返回 token。"""
    return set_default(Session(user, pwd)).login(fatal=fatal)


def login_as(user, pwd, fatal=False):
    """登录但**不**改默认会话；失败返回 None（不致命，调用方自行判红/跳过）。"""
    return Session(user, pwd).login(fatal=fatal)


# ---- 模块级便捷入口（等价于 default().xxx）----
def call(method, path, body=None, extra=None):
    return default().call(method, path, body, extra)


def call_full(method, path, body=None, extra=None):
    """返回 `(status, body, headers)`；要 `ETag` 时用它。"""
    return default().request(method, path, body, extra)


def call_as(hdr, method, path, body=None, extra=None):
    """用**指定身份**的请求头发请求（审批/渠道等 SoD 场景），返回 `(status, body)`。"""
    h = dict(hdr)
    if extra:
        h.update(extra)
    s, d, _ = raw_call(default().base, method, path, body, h)
    return s, d


def session_headers(token, tenant=None):
    """由 token 直接拼一份身份头（存起来给 `call_as` 用）。"""
    return {
        "Authorization": "Bearer " + (token or ""),
        "X-Tenant-Id": tenant if tenant is not None else config.TENANT,
        "Content-Type": "application/json",
    }


# ---------------------------------------------------------------- 响应解析
def ecode(d):
    """取业务错误码 `data.errorCode`；成功或非结构化响应返回 None。"""
    data = d.get("data") if isinstance(d, dict) else None
    return data.get("errorCode") if isinstance(data, dict) else None


def d_(d, *keys, default=None):
    """按路径取值：`d_(resp, "data", "records")`；任一层不是 dict 或取不到 ⇒ 返回 default。"""
    cur = d.get("data")
    for k in keys:
        if not isinstance(cur, dict):
            return default
        cur = cur.get(k)
    return default if cur is None else cur


def etag_of(headers):
    """从响应头取 ETag（大小写不敏感）。"""
    if not headers:
        return None
    return headers.get("ETag") or headers.get("Etag") or headers.get("etag")


def jwt_user_id(token):
    """从 JWT payload 取 `userId` 并转 int。

    审批人身份口径必须是**账户数字 id**（与 `RuntimeActionContext#getOperatorId` 同源），
    不是登录名：工作流 FIELD 指派会把它原样落 `wf_task.assignee_id`，口径不一致 ⇒ 任务级 SoD 恒 403。
    """
    try:
        p = token.split(".")[1]
        p += "=" * (-len(p) % 4)
        uid = json.loads(base64.urlsafe_b64decode(p)).get("userId")
        return int(uid) if uid is not None else None
    except Exception:
        return None
