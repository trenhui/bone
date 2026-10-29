#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""{{DESC}}

模板来源：scripts/e2e/templates/py-sweep.py.tpl（由 scaffold.py 生成，可安全改写）

用法：
  python3 scripts/e2e/{{FILE}}
  BONE_E2E_JUNIT_DIR=/tmp/e2e-junit python3 scripts/e2e/{{FILE}}   # 出 JUnit XML

骨架自带四条纪律（改用例时别删——它们是「可复用」的全部内容）：
  1. 地址/凭据只从 e2e_lib.config 取：脚本内**不出现** host:port 与口令（README §2.1，gitleaks 友好）；
  2. 造数只从 e2e_lib.fixtures 取：可种子化，同 seed 跨进程同值（README §3，残留可归因）；
  3. 断言只走 e2e_lib.report：失败详情必须写**实测值**（状态码/错误码/报文片段），否则红了没法定位；
  4. 结果出口是**退出码**（+可选 JUnit XML），不靠人读控制台（README §5）。

注意：`config.BASE` **已含** `/api/v1`，`http.call("GET", "/apps")` 才是正确写法；
再写 `/api/v1/apps` 会拼成 `/api/v1/api/v1/apps`（404）——踩过，别重踩。
"""
from e2e_lib import config, http
from e2e_lib import fixtures as fx
from e2e_lib.report import check, finish, note, section

# 运行级后缀：本次运行产物的命名与定点回收都靠它（cleanup-residue.py --suffix）
SUFFIX = config.run_suffix()
DS = fx.dataset(SUFFIX)

# 待测目标：模板给最小样本，替换成你真正要扫的东西
TARGETS = [
    "/iam/menus/tree",   # IAM：菜单树
    "/apps",             # metadata：应用列表
]


def main():
    section("{{DESC}}")
    note(f"BASE={config.BASE}  USER={config.USER}  SUFFIX={SUFFIX}")
    http.login()  # 主会话；失败直接退出（没登录跑什么都是假红，不要用「跳过」掩盖）

    for path in TARGETS:
        st, body = http.call("GET", path)
        # 判据只认状态码与报文；详情写实测值（st + 报文片段），不写「应该 200」这种预期值
        check(f"GET {path} 可读", st == 200, f"status={st} body={str(body)[:160]}")

    # ---- 负向对照（没有它的用例等于没测：正向全绿但断言恒真 = 假绿）----
    st, _ = http.call("GET", "/__e2e_not_exist__")
    check("不存在的路径不会被当成 200", st == 404, f"status={st}")
    st, _ = http.call_as(http.session_headers(""), "GET", TARGETS[0])
    check("匿名访问被拒(401)", st == 401, f"status={st}")
    return 0


if __name__ == "__main__":
    main()
    finish("{{DESC}} 汇总")  # 内部按断言结果 sys.exit(0/1)，正是 CI 读的退出码
