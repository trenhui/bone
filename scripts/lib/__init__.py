"""scripts/lib —— 工程脚本共享层。

`gate.py` 提供判据横切能力契约，`registry.py` 提供门禁注册表解析与对账。
业务脚本用 `from lib import gate, registry` 导入（调用方需把 scripts/ 加入 sys.path）。
"""
