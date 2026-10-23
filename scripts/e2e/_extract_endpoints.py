#!/usr/bin/env python3
"""从 Controller 源码提取端点清单（类级 @RequestMapping + 方法级 @XxxMapping）。

支持类级路径写成常量表达式（PlatformApiPaths.MASTERDATA_V1 + "/lineage"）。
输出 JSON: [{module, controller, method, http, path, has_path_var}]
仅用于联调扫描，不进入门禁。
"""
import json
import os
import re
import sys

ROOTS = ["bone-platform", "bone-engine", "bone-apps", "bone-ai", "bone-framework"]

# 方法级：@GetMapping / @GetMapping("/x") / @GetMapping(value="/x") / @GetMapping(PathX + "/y")
METHOD_RE = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping\(([^)]*)\)', re.M
)
# 类级：@RequestMapping(@Xxx) / @RequestMapping("...") / @RequestMapping(A + "/b")
CLASS_RE = re.compile(r'@RequestMapping\(([^)]*)\)', re.M)
CONST_RE = re.compile(r'\b([A-Z][A-Z0-9_]{2,})\s*=\s*"([^"]*)"')


def load_consts():
    consts = {}
    for root in ROOTS:
        for dp, dn, fns in os.walk(root):
            if "node_modules" in dp or "/target/" in dp:
                continue
            for fn in fns:
                if fn.endswith(".java") and "ApiPaths" in fn:
                    try:
                        src = open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read()
                    except OSError:
                        continue
                    for m in CONST_RE.finditer(src):
                        consts[m.group(1)] = m.group(2)
    return consts


CONSTS = load_consts()


def resolve_expr(expr: str) -> str:
    """把 `PlatformApiPaths.MASTERDATA_V1 + "/lineage"` 解析成字面路径。"""
    if expr is None:
        return ""
    expr = expr.strip()
    # 只含一个字符串字面量
    m = re.fullmatch(r'"?\s*(?:value\s*=\s*)?"([^"]*)"\s*', expr)
    if m:
        return m.group(1)
    parts = []
    for piece in expr.split("+"):
        piece = piece.strip().strip('"')
        if not piece:
            continue
        piece = re.sub(r'^\s*(?:value|path)\s*=\s*', '', piece).strip()
        if not piece:
            continue
        if "." in piece:
            piece = piece.split(".")[-1]
        parts.append(CONSTS.get(piece, piece))
    out = "".join(parts)
    return out if out.startswith("/") else ""


def module_of(path: str) -> str:
    parts = path.split(os.sep)
    return parts[0] + "/" + parts[1] if len(parts) >= 2 else parts[0]


def main() -> int:
    out = []
    seen = set()
    for root in ROOTS:
        for dirpath, dirnames, filenames in os.walk(root):
            if "node_modules" in dirpath or "/target/" in dirpath:
                continue
            for fn in filenames:
                if not fn.endswith("Controller.java"):
                    continue
                if os.sep + "test" + os.sep in dirpath:
                    continue
                fp = os.path.join(dirpath, fn)
                try:
                    src = open(fp, encoding="utf-8", errors="ignore").read()
                except OSError:
                    continue
                cls = ""
                m = CLASS_RE.search(src)
                if m:
                    cls = resolve_expr(m.group(1))
                for mm in METHOD_RE.finditer(src):
                    verb = mm.group(1).upper()
                    sub = resolve_expr(mm.group(2))
                    path = (cls + sub).replace("//", "/")
                    if not path.startswith("/"):
                        continue
                    key = (fn, verb, path)
                    if key in seen:
                        continue
                    seen.add(key)
                    out.append(
                        {
                            "module": module_of(fp),
                            "controller": fn[:-5],
                            "http": verb,
                            "path": path,
                            "has_var": "{" in path,
                        }
                    )
    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
