#!/usr/bin/env python3
"""拓扑图端口一致性门禁（G41）：README 运行时拓扑图 ↔ 代码端口真源。

## 为什么需要这条门禁
`doc/design/assets/readme-runtime-topology.svg` 是手写 SVG，而端口真源是各模块
`application.yml` 与前端 `vite.config.ts`。两者之间**没有任何绑定**，于是「加了一个模块
但图没改」不会让任何门禁变红——2026-09-28 的画法到 2026-10-31 已漂移 6 处
（bone-file 已实现却标「规划」、blueprint/extension-studio/synap-claim 整块缺失、
前端漏 3012/3013、bone-ai 整层缺失）。这类失真比"图不好看"严重得多：
读者按图启动会找不到扩展控制面，按图理解架构会以为平台没有 AI 域。

## 判据（三条，任一不满足即红）
R1 无幽灵端口：图上出现的每个端口，必须能在某个 `application.yml`/`vite.config.ts` 里找到，
   或属于基础设施白名单（MySQL 3306 / Redis 6379，由 docker compose 承载，无 yml 可比对）。
   反向证明：改了服务端口却忘改图 ⇒ 图上是旧端口 ⇒ R1 红。
R2 必绘模块齐全：`REQUIRED_MODULES` 里登记的模块，其标签名与其真源端口都必须出现在图上。
   反向证明：新增/漏画模块 ⇒ R2 红。
R3 前端子应用齐全：所有 `createQiankunViteConfig(name, port)` 的端口都要在图上。

另有「提示」（不改退出码）：真源里存在、但图里没画的后端端口。用它发现"新增服务没上图"，
同时不强迫把不面向开发者的模块塞进"开发环境拓扑"。

## 用法
    python3 scripts/check-runtime-topology.py            # 默认校验仓库内拓扑图
    python3 scripts/check-runtime-topology.py --svg /tmp/mutated.svg   # 变异验证用
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# 基础设施端口：由 docker compose 承载，没有 application.yml 可当真源。
INFRA_PORTS = {3306, 6379}

# 必绘模块：模块目录 → 图上的标签名。新增面向开发者启动的服务时，请同时改这里与 SVG，
# 否则 R2 会红——这是「图必须跟着代码走」的唯一人工登记点。
REQUIRED_MODULES = {
    "bone-platform/bone-gateway": "bone-gateway",
    "bone-platform/bone-iam": "bone-iam",
    "bone-platform/bone-system": "bone-system",
    "bone-platform/bone-masterdata": "bone-masterdata",
    "bone-platform/bone-integration": "bone-integration",
    "bone-platform/bone-file": "bone-file",
    "bone-blueprint": "bone-blueprint",
    "bone-apps/synap-claim": "synap-claim",
    "bone-engine/bone-metadata-server": "bone-metadata-server",
    "bone-engine/bone-extension-engine/bone-extension-studio": "bone-extension-studio",
    "bone-engine/studio-generator": "studio-generator",
    "bone-ai/bone-ai-app": "bone-ai-app",
}

_YAML_PORT_RE = re.compile(r"^(\s+)port:\s*(\S+)\s*$")
_DEFAULT_RE = re.compile(r"^\$\{[^:}]+:(\d+)\}$")
_VITE_PORT_RE = re.compile(r"createQiankunViteConfig\(\s*'([^']+)'\s*,\s*(\d{4,5})")
_VITE_SERVER_PORT_RE = re.compile(r"port:\s*(\d{4,5})")
_SVG_PORT_RE = re.compile(r":(\d{4,5})")


def parse_server_port(text: str) -> int | None:
    """从 application.yml 文本里取 server.port；支持 `${VAR:8081}` 形式并取默认值。

    只认 `server:` 顶层块内缩进 2 的 `port:`，避免把 `spring.redis.port: 6379`
    这类嵌套端口误当服务端口（本仓 blueprint / extension-studio 的 dev 配置都有这个坑）。
    """
    in_server = False
    for line in text.splitlines():
        if re.match(r"^server:\s*$", line):
            in_server = True
            continue
        if not in_server:
            continue
        if line.strip() and not line.startswith((" ", "\t")):
            return None  # 已离开 server 块，没找到 port
        m = _YAML_PORT_RE.match(line)
        if not m:
            continue
        if len(m.group(1)) != 2:
            continue
        raw = m.group(2).strip("'\"")
        if raw.isdigit():
            return int(raw)
        dm = _DEFAULT_RE.match(raw)
        if dm:
            return int(dm.group(1))
    return None


def collect_backend_ports(repo: Path) -> dict[str, int]:
    """扫描所有 src/main/resources/application*.y(a)ml，返回 {模块相对路径: 端口}。"""
    ports: dict[str, int] = {}
    for path in sorted(repo.glob("**/src/main/resources/application*.y*ml")):
        parts = path.parts
        if "target" in parts or "test" in parts:
            continue
        # …/<module>/src/main/resources/application.yml → <module>
        module = str(path.parent.parent.parent.parent.relative_to(repo))
        port = parse_server_port(path.read_text(encoding="utf-8", errors="ignore"))
        if port is not None:
            ports[module] = port
    return ports


def collect_frontend_ports(repo: Path) -> dict[str, int]:
    """前端子应用 dev 端口：createQiankunViteConfig('name', port)，shell 用 server.port。"""
    ports: dict[str, int] = {}
    apps = repo / "bone-frontend" / "apps"
    if not apps.is_dir():
        return ports
    for cfg in sorted(apps.glob("*/vite.config.ts")):
        text = cfg.read_text(encoding="utf-8", errors="ignore")
        m = _VITE_PORT_RE.search(text)
        if m:
            ports[m.group(1)] = int(m.group(2))
            continue
        m = _VITE_SERVER_PORT_RE.search(text)
        if m:
            ports[cfg.parent.name] = int(m.group(1))
    return ports


def extract_svg_ports(text: str) -> set[int]:
    return {int(m) for m in _SVG_PORT_RE.findall(text)}


def validate(
    svg_text: str,
    backend: dict[str, int],
    frontend: dict[str, int],
    required: dict[str, str] | None = None,
    infra: set[int] | None = None,
) -> tuple[list[str], list[str]]:
    """纯函数判据：返回 (errors, warnings)。便于单测直接喂构造数据。"""
    required = REQUIRED_MODULES if required is None else required
    infra = INFRA_PORTS if infra is None else infra

    svg_ports = extract_svg_ports(svg_text)
    errors: list[str] = []
    warnings: list[str] = []

    # R1 无幽灵端口
    known = set(backend.values()) | set(frontend.values()) | set(infra)
    for port in sorted(svg_ports - known):
        errors.append(
            f"R1 图中端口 :{port} 在任何 application.yml / vite.config.ts 中都不存在"
            f"（服务已改端口却忘改图，或端口写错）"
        )

    # R2 必绘模块齐全（标签名 + 真源端口都要在）
    for module, label in required.items():
        expected = backend.get(module)
        if expected is None:
            warnings.append(f"R2 跳过 {module}：其 application.yml 未显式声明 server.port")
            continue
        if label not in svg_text:
            errors.append(f"R2 图上缺少模块标签 `{label}`（{module}）")
        if expected not in svg_ports:
            errors.append(f"R2 图上缺少 `{label}` 的端口 :{expected}（{module}）")

    # R3 前端子应用齐全
    for name, port in sorted(frontend.items()):
        if port not in svg_ports:
            errors.append(f"R3 图上缺少前端子应用 `{name}` 的端口 :{port}")

    # 提示：真源有、图上无（不判红，避免强迫把非开发者面向的模块塞进图）
    drawn = svg_ports
    for module, port in sorted(backend.items()):
        if port not in drawn:
            warnings.append(f"提示 {module}（:{port}）有端口但未画入拓扑图")

    return errors, warnings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="拓扑图端口一致性门禁（G41）")
    parser.add_argument("--svg", default=str(REPO / "doc/design/assets/readme-runtime-topology.svg"))
    parser.add_argument("--repo", default=str(REPO))
    parser.add_argument("--quiet", action="store_true", help="只输出失败项")
    args = parser.parse_args(argv)

    repo = Path(args.repo)
    svg_path = Path(args.svg)
    if not svg_path.is_file():
        print(f"❌ 拓扑图不存在：{svg_path}", file=sys.stderr)
        return 1

    backend = collect_backend_ports(repo)
    frontend = collect_frontend_ports(repo)
    svg_text = svg_path.read_text(encoding="utf-8", errors="ignore")
    errors, warnings = validate(svg_text, backend, frontend)

    if not args.quiet:
        print(
            f"   真源：后端 {len(backend)} 个端口 / 前端 {len(frontend)} 个子应用"
            f"；图内端口 {sorted(extract_svg_ports(svg_text))}"
        )
        for w in warnings:
            print(f"   ⚠️  {w}")

    if errors:
        for e in errors:
            print(f"   ❌ {e}", file=sys.stderr)
        print(f"❌ 拓扑图与代码端口真源不一致（{len(errors)} 处）", file=sys.stderr)
        return 1

    print("✅ 拓扑图端口与代码真源一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
