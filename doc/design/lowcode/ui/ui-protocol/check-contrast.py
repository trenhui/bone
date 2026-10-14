#!/usr/bin/env python3
"""check-contrast.py — bone-ui-schema-designer.html 配色对比度门禁（WCAG 2.1）

机器校验设计器 CSS 的文本/图形对比度：
  - 正文与次要文字：>= 4.5:1（AA）
  - 徽标/标签/提示等小字（<=11px）：>= 4.5:1（AA）
  - 图形元素（圆点/开关钮/描边）：>= 3:1（AA 非文本）
色值全部从 HTML 内联 CSS 实时解析（变量块 + 字面量规则），改色即重新判定。
用法：python3 check-contrast.py [html路径]   退出码 0=全过，1=有 FAIL
"""
import re
import sys

PATH = sys.argv[1] if len(sys.argv) > 1 else "bone-ui-schema-designer.html"

# ---------- WCAG 计算 ----------
def _lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def lum(rgb):
    r, g, b = rgb
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)

def ratio(fg, bg):
    l1, l2 = lum(fg), lum(bg)
    if l1 < l2:
        l1, l2 = l2, l1
    return (l1 + 0.05) / (l2 + 0.05)

def hx(s):
    s = s.strip()
    if s.startswith("#"):
        s = s[1:]
        if len(s) == 3:
            s = "".join(c * 2 for c in s)
        return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
    m = re.match(r"rgba?\(([^)]+)\)", s)
    if m:
        parts = [p.strip() for p in m.group(1).split(",")]
        return tuple(int(float(p)) for p in parts[:3]), float(parts[3]) if len(parts) > 3 else 1.0
    raise ValueError("bad color: " + s)

def parse_color(s):
    """返回 (rgb, alpha)"""
    c = hx(s)
    if isinstance(c, tuple) and len(c) == 2 and isinstance(c[1], float):
        return c
    return c, 1.0

def blend(fg, alpha, bg):
    return tuple(round(bg[i] * (1 - alpha) + fg[i] * alpha) for i in range(3))

# ---------- 解析 HTML ----------
src = open(PATH, encoding="utf-8").read()

def theme_vars(name):
    m = re.search(re.escape(name) + r"\s*\{(.*?)\n\s*\}", src, re.S)
    if not m:
        sys.exit("FATAL: 找不到主题块 " + name)
    d = {}
    for k, v in re.findall(r"--([a-z0-9-]+)\s*:\s*([^;}]+)", m.group(1)):
        d[k] = v.strip()
    return d

LIGHT = theme_vars(":root")
DARK = theme_vars('[data-theme="dark"]')

def grad_stops(vars_):
    m = re.search(r"linear-gradient\([^,]*,\s*(#[0-9a-fA-F]{3,6})\s*,\s*(#[0-9a-fA-F]{3,6})\)",
                  vars_.get("grad", LIGHT["grad"]))
    return [parse_color(m.group(1))[0], parse_color(m.group(2))[0]]

def rule_color(selector, prop):
    """从 CSS 规则里取 color/background 字面量；selector 只写选择器本身（不含 {）"""
    m = re.search(re.escape(selector) + r"\{[^}]*" + prop + r":\s*([^;}]+)", src)
    if not m:
        sys.exit("FATAL: 选择器未匹配到 " + prop + " -> " + selector)
    return m.group(1).strip()

# ---------- 断言表 ----------
checks = []  # (theme, 名称, fg, bg, 最小比值, 类型)

def add(theme, name, fg_hex, bg_hex, minr, kind="text"):
    fg, _ = parse_color(fg_hex)
    bg, _ = parse_color(bg_hex)
    checks.append((theme, name, fg, bg, minr, kind))

def add_tint(theme, name, fg_hex, tint_literal, panel_hex, minr=4.5):
    """前景色放在「tint 半透明叠在 panel 上」的背景"""
    fg, _ = parse_color(fg_hex)
    tr, ta = parse_color(tint_literal)
    panel, _ = parse_color(panel_hex)
    bg = blend(tr, ta, panel)
    checks.append((theme, name, fg, bg, minr, "text"))

for tname, tv in (("light", LIGHT), ("dark", DARK)):
    P, P2, B = tv["panel"], tv["panel2"], tv["bg"]
    # 1) 正文/次要文字
    add(tname, "正文 ink/panel", tv["ink"], P, 4.5)
    add(tname, "正文 ink/panel2", tv["ink"], P2, 4.5)
    add(tname, "正文 ink/bg", tv["ink"], B, 4.5)
    add(tname, "次要 sub/panel", tv["sub"], P, 4.5)
    add(tname, "次要 sub/panel2", tv["sub"], P2, 4.5)
    # 2) 状态色作文字（徽标/提示/链接）
    for c in ("ok", "warn", "err", "info", "pri"):
        add(tname, f"{c}/panel", tv[c], P, 4.5)
        add(tname, f"{c}/panel2", tv[c], P2, 4.5)
    # 3) 图标钮 alert：err 文字 on err-weak 底
    ew = tv["err-weak"]
    er, ea = parse_color(ew)
    panel_rgb, _ = parse_color(P)
    add(tname, "err/err-weak(图标钮)", tv["err"], "#%02x%02x%02x" % blend(er, ea, panel_rgb), 4.5)
    # 4) 徽标/标签：文字 on 同色相 tint（叠 panel）
    tint_map = [
        ("badge.e 错误徽标", "err", ".badge.e"),
        ("badge.w 警告徽标", "warn", ".badge.w"),
        ("badge.ok 成功徽标", "ok", ".badge.ok"),
        ("pill.v 变体徽标", "ok", ".pill.v"),
        ("tag.s-REVIEWING", "warn", ".tag.s-REVIEWING"),
        ("tag.s-APPROVED", "ok", ".tag.s-APPROVED"),
        ("tag.s-REJECTED", "err", ".tag.s-REJECTED"),
        ("tag.s-SUBMITTED", "info", ".tag.s-SUBMITTED"),
        ("tag.s-PAID", "pri", ".tag.s-PAID"),
        ("perf-badge 性能", "ok", ".perf-badge"),
        ("perf-badge.warn", "warn", ".perf-badge.warn"),
        ("bool.y 布尔真", "ok", ".bool.y"),
        ("banner 信息条", "info", ".banner"),
        ("alertbox 警告框", "warn", ".alertbox"),
        ("aib 页脚徽标", "pri", ".footer .aib"),
    ]
    for name, var, sel in tint_map:
        add_tint(tname, name, tv[var], rule_color(sel, "background"), P)
    # alertbox.crit：err on err tint
    add_tint(tname, "alertbox.crit 严重框", tv["err"],
             rule_color(".alertbox.crit", "background"), P)
    # badge.pri：pri on pri-soft
    add(tname, "badge.pri", tv["pri"], tv["pri-soft"], 4.5)

# 5) 硬编码徽标色（浅色规则 + 暗色覆盖规则分别取）
add_tint("light", "badge.v 变体徽标", rule_color(".badge.v", "color"),
         rule_color(".badge.v", "background"), LIGHT["panel"])
add_tint("light", "tag.s-DRAFT 草稿", rule_color(".tag.s-DRAFT", "color"),
         rule_color(".tag.s-DRAFT", "background"), LIGHT["panel"])
add_tint("dark", "badge.v 变体徽标", rule_color('[data-theme="dark"] .badge.v', "color"),
         rule_color('[data-theme="dark"] .badge.v', "background"), DARK["panel"])
add_tint("dark", "tag.s-DRAFT 草稿", rule_color('[data-theme="dark"] .tag.s-DRAFT', "color"),
         rule_color('[data-theme="dark"] .tag.s-DRAFT', "background"), DARK["panel"])

# 6) 白字/深色字放在实色底上（按钮、dual 徽章、chip.on）
for tname, tv in (("light", LIGHT), ("dark", DARK)):
    for i, stop in enumerate(grad_stops(tv)):
        add(tname, f"白字/渐变停靠点{i+1}（主按钮/FAB/logo）", "#ffffff",
            "#%02x%02x%02x" % stop, 4.5)
    dual_fg = rule_color(".abtn .dual", "color") if tname == "light" else \
        rule_color('[data-theme="dark"] .abtn .dual', "color")
    add(tname, "dual 徽章字/warn 底", dual_fg, tv["warn"], 4.5)
    chip_fg = "#ffffff" if tname == "light" else \
        rule_color('[data-theme="dark"] #railInspect .chip.on', "color")
    add(tname, "chip.on 字/pri 底", chip_fg, tv["pri"], 4.5)

# 7) 图形元素 3:1（圆点/时间线点/开关钮）
for tname, tv in (("light", LIGHT), ("dark", DARK)):
    add(tname, "草稿点 ok/panel（图形）", tv["ok"], tv["panel"], 3.0, "graphic")
    add(tname, "脏记号点 warn/panel（图形）", tv["warn"], tv["panel"], 3.0, "graphic")
    add(tname, "时间线点 pri/panel（图形）", tv["pri"], tv["panel"], 3.0, "graphic")
    add(tname, "开关钮白/pri（图形）", "#ffffff", tv["pri"], 3.0, "graphic")

# ---------- 输出 ----------
fails = []
print(f"{'主题':6} {'检查项':34} {'比值':>8}  判定")
print("-" * 66)
for theme, name, fg, bg, minr, kind in checks:
    r = ratio(fg, bg)
    okk = r >= minr
    if not okk:
        fails.append((theme, name, r, minr))
    print(f"{theme:6} {name:34} {r:7.2f}:1  {'PASS' if okk else 'FAIL'} (需>={minr})")

print("-" * 66)
print(f"共 {len(checks)} 项，FAIL {len(fails)} 项")
if fails:
    for f in fails:
        print("  FAIL:", f)
    sys.exit(1)
print("全部达标（WCAG 2.1 AA）")
