#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""{{DESC}}

模板来源：scripts/e2e/templates/py-chain.py.tpl（由 scaffold.py 生成，可安全改写）

用法：
  python3 scripts/e2e/{{FILE}}
  BONE_E2E_JUNIT_DIR=/tmp/e2e-junit python3 scripts/e2e/{{FILE}}   # 出 JUnit XML

骨架说明：链路**只声明数据**（步骤字典），「发请求→取变量→落断言→传下一步」由
e2e_lib.chain_runner.run_steps 统一执行（README §9）。新增一条链路 = 加一段数据，
不再往单体大文件里抄样板。

步骤字段（全可选）：method/path/body/token/call/when/checks/extract/stop_if_empty/post
判据 kind：status | eq | cond | err | page | list（语义见 e2e_lib/chain_runner.py 头注释）

注意：`config.BASE` **已含** `/api/v1`，path 写 `/apps` 而不是 `/api/v1/apps`。
"""
from e2e_lib import config, http
from e2e_lib import fixtures as fx
from e2e_lib import chain_runner
from e2e_lib.chain_runner import ChainCtx, run_steps
from e2e_lib.report import check, finish, note, section

SUFFIX = config.run_suffix()
DS = fx.dataset(SUFFIX)


def rpc(method, path, body, token=None):
    """run_steps 的请求入口，签名固定为 (method, path, body, token) -> (status, body)。

    token 为 None 时走主会话；非 None（含显式传入的空串）走 call_as —— 负向用例
    （匿名/越权）就靠传空串或他人 token，不需要另写一套请求函数。
    """
    if token is None:
        return http.call(method, path, body)
    return http.call_as(http.session_headers(token), method, path, body)


def data_of(j):
    return (j or {}).get("data") or {}


def emit(name, expect, actual, detail=""):
    """把 chain_runner 的判据接到 e2e_lib.report（统一汇总 / JUnit / 退出码）。

    判定语义复用 chain_runner.judge（谓词 > 元组任一命中 > 相等），不另造一套，
    免得与 chains.py 的判据口径分裂。
    """
    check(name, chain_runner.judge(expect, actual), detail)


def build_steps():
    """链路定义：只读三段样板（列表→详情→负向），替换成你的真实链路。

    换成写链路时注意三点，否则会造出假绿：
      * 每一步都要有判据，「只取数不断言」的步骤会让后面的失败归因错位；
      * 取到的 id/编码用 `stop_if_empty` 止损，别让后续步骤拿着 None 发请求；
      * 末段必须有**负向**步骤（越权/非法入参/重复提交），否则断言可能恒真。
    """
    return [
        "— {{DESC}} —",
        # 1. 列表：取第一条 id；取不到即结束本链
        {"method": "GET", "path": "/apps",
         "extract": {"rid": lambda c, st, j: ((data_of(j).get("records") or [{}])[0]).get("id")},
         "checks": [{"name": "应用列表可读", "kind": "status", "expect": 200,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:160]}"}],
         "stop_if_empty": ["rid"]},
        # 2. 详情：`{rid}` 由 run_steps 用上一步的变量替换；断言 id 一致（防串台）
        {"method": "GET", "path": "/apps/{rid}",
         "checks": [{"name": "详情 id 与列表一致（未串台）", "kind": "cond", "expect": True,
                     "actual": lambda c, st, j: str(data_of(j).get("id")) == str(c.rid),
                     "detail": lambda c, st, j: f"status={st} rid={c.rid} got={data_of(j).get('id')}"}]},
        # 3. 负向：匿名必须被拒（恒 200 的断言没有判别力）
        {"method": "GET", "path": "/apps", "token": "",
         "checks": [{"name": "匿名访问被拒(401)", "kind": "status", "expect": 401,
                     "detail": lambda c, st, j: f"status={st}"}]},
        # 4. 负向：路径不存在必须是 404，而不是被 catch-all 兜成 200/500
        {"method": "GET", "path": "/__e2e_not_exist__",
         "checks": [{"name": "不存在路径为 404", "kind": "status", "expect": 404,
                     "detail": lambda c, st, j: f"status={st}"}]},
    ]


def main():
    section("{{DESC}}")
    note(f"BASE={config.BASE}  USER={config.USER}  SUFFIX={SUFFIX}")
    tok = http.login()
    ctx = ChainCtx(rpc, main=tok, cid="{{SUITE}}")
    run_steps(build_steps(), ctx, emit=emit)
    return 0


if __name__ == "__main__":
    main()
    finish("{{DESC}} 汇总")  # 内部按断言结果 sys.exit(0/1)，正是 CI 读的退出码
