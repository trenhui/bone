# -*- coding: utf-8 -*-
"""e2e_lib —— scripts/e2e 可复用公共层。

所有 E2E 脚本（Python 全链路 / sweep / channel）统一从这里取基础设施，
禁止再在脚本内复制 login/call/check/teardown 样板：

    from e2e_lib import config, http, report, modeling, runtime, teardown, mysql, sign

    config   环境配置唯一真源（BASE/凭据/样例路径/MySQL/残留策略，全走环境变量）
    http     HTTP 与会话（login / call / call_as / ecode / d_ / etag_of / jwt_user_id）
    sign     渠道回调签名（hmac_sign，Mock 验签口径）
    report   断言与报告（check / section / note / summary / finish）
    modeling 建模侧（create_entity / ensure_field / child_table_field / add_action /
             put_schema / validate / publish）
    runtime  业务侧（rec_create / child_replace / act / act_as / rec_status）
    teardown 运行级残留自动回收（register / clean，执行体 cleanup-residue.py）
    mysql    第二证据源直查（query -> (stdout, stderr)）

脚本位于 scripts/e2e/ 下，`sys.path[0]` 即脚本目录，直接 `import e2e_lib` 无需 bootstrap。
"""
from . import config, http, modeling, mysql, report, runtime, sign, teardown  # noqa: F401

__all__ = ["config", "http", "modeling", "mysql", "report", "runtime", "sign", "teardown"]
