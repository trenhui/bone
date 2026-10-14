#!/usr/bin/env python3
"""check-contrast.py — bone-ui-schema-designer.html 配色对比度门禁（WCAG 2.1）

机器校验设计器 CSS 的文本/图形对比度（色值全部从 HTML 内联 CSS 实时解析，改色即重判）：
  - 正文/次要文字/徽标标签小字：>= 4.5:1（AA）
  - 图形元素（状态圆点/时间线点/章节左边框）：>= 3:1（AA 非文本）
  - 渐变停靠上的白字（主按钮/logo/FAB）：每个停靠 >= 4.5:1

覆盖 v2.0 语义色体系：主题变量（pri/pri2/ok/warn/err/sub/ink）、
st-chip 状态族（st-info/st-ok/st-warn/st-err/st-gray）、diff 语义（dchip/dot/dsec）、
徽标族（vtag/json-status/submit-msg/lint-item lv/pill.link/ctx-badge.auto/tag-off/scope-chip/ov-badge）、
提示族（notice/tenant-note/alert-box/fld .ro/trace chip）、双主题（浅色规则 + 暗色覆盖规则分别取）。

装饰性豁免（不承载语义、旁边必有文字标签，WCAG 1.4.11 不适用）：
  header::after 渐变饰线、--amb/--glow 环境光与投影、dsec/dchip 的彩色描边、
  .timeline 竖线、.draft-dot.dirty 的光环。

用法：python3 check-contrast.py [html路径]   退出码 0=全过，1=有 FAIL
任何选择器/变量匹配不到 → FATAL 退出（门禁不许静默跳过）。
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

def resolve(literal, tv):
    """把 var(--x) 替换为主题变量值（最多展开 5 层，防呆）"""
    out = literal
    for _ in range(5):
        m = re.search(r"var\(--([a-z0-9-]+)\)", out)
        if not m:
            return out
        if m.group(1) not in tv:
            sys.exit("FATAL: 变量未定义 --" + m.group(1) + "（出现在 " + literal + "）")
        out = out[: m.start()] + tv[m.group(1)] + out[m.end():]
    return out

def _find_rule(selector):
    """取 selector 所在规则块（selector 可为逗号分组中任意成员：跳过后续成员直到 {）。
    锚定选择器起点（行首或逗号后）且必须是完整成员（后随 , { 或空白），
    避免 .st-ok 误配 .st-okx、.pill 误配 .pill.link、浅色规则被暗色覆盖规则抢先。"""
    pat = re.compile(r"(?m)(?:^|,)\s*" + re.escape(selector) + r"(?=\s*[ ,{])")
    for m in pat.finditer(src):
        gm = re.match(r"(?:\s*,[^,{]+)*\s*\{([^}]*)\}", src[m.end():])
        if gm:
            return gm.group(1)
    return None

def _prop_of(block, prop):
    """从规则块取属性值；属性名锚定边界，防止 color: 误匹配 border-color: 的子串。"""
    m = re.search(r"(?<![a-z-])" + re.escape(prop) + r":\s*([^;}]+)", block)
    return m.group(1).strip() if m else None

def rule_color(selector, prop):
    """从 CSS 规则取 color/background 字面量；selector 只写选择器本身（不含 {）。"""
    block = _find_rule(selector)
    if block is None:
        sys.exit("FATAL: 选择器未匹配到规则 -> " + selector)
    v = _prop_of(block, prop)
    if v is None:
        sys.exit("FATAL: 选择器未匹配到 " + prop + " -> " + selector)
    return v

def try_rule_color(selector, prop):
    block = _find_rule(selector)
    if block is None:
        return None
    return _prop_of(block, prop)

def pick(selector, prop, theme):
    """按主题取规则字面量：暗色优先用 [data-theme="dark"] 覆盖规则，否则用浅色规则并按主题解析变量。"""
    tv = LIGHT if theme == "light" else DARK
    if theme == "dark":
        v = try_rule_color('[data-theme="dark"] ' + selector, prop)
        if v:
            return resolve(v, tv)
    return resolve(rule_color(selector, prop), tv)

def grad_stops(vars_):
    """解析 --grad 全部停靠色（支持任意停靠数）"""
    return [parse_color(h)[0] for h in re.findall(r"#[0-9a-fA-F]{3,6}", vars_["grad"])]

def rule_grad_stops(selector, prop, theme):
    """从规则取 linear-gradient 字面量并解析 var() 后提取停靠色（暗色优先用覆盖规则）。"""
    tv = LIGHT if theme == "light" else DARK
    if theme == "dark":
        v = try_rule_color('[data-theme="dark"] ' + selector, prop)
        if v:
            return [parse_color(h)[0] for h in re.findall(r"#[0-9a-fA-F]{3,6}", resolve(v, tv))]
    lit = resolve(rule_color(selector, prop), tv)
    return [parse_color(h)[0] for h in re.findall(r"#[0-9a-fA-F]{3,6}", lit)]

# ---------- 断言表 ----------
checks = []  # (theme, 名称, fg, bg, 最小比值, 类型)

def add(theme, name, fg_hex, bg_hex, minr, kind="text"):
    fg, _ = parse_color(fg_hex)
    bg, _ = parse_color(bg_hex)
    checks.append((theme, name, fg, bg, minr, kind))

def add_tint(theme, name, fg_hex, tint_literal, panel_hex, minr=4.5):
    """前景色放在「tint 半透明（或实色）叠在 panel 上」的背景"""
    fg, _ = parse_color(fg_hex)
    tr, ta = parse_color(tint_literal)
    panel, _ = parse_color(panel_hex)
    bg = blend(tr, ta, panel)
    checks.append((theme, name, fg, bg, minr, "text"))

# tint_map: (名称, fg 选择器或 ("var", 名), bg 选择器或 ("var", 名))
# fg/bg 均可为：("var", "pri") 主题变量；("rule", 选择器, 属性) CSS 规则字面量
TINT_MAP = [
    ("st-info 状态 chip",            ("var", "pri"),  ("rule", ".st-info", "background")),
    ("st-ok 状态 chip",              ("var", "ok"),   ("rule", ".st-ok", "background")),
    ("st-warn 状态 chip",            ("var", "warn"), ("rule", ".st-warn", "background")),
    ("st-err 状态 chip",             ("var", "err"),  ("rule", ".st-err", "background")),
    ("st-gray 状态 chip",            ("var", "sub"),  ("rule", ".st-gray", "background")),
    ("dchip.add 差异新增",           ("rule", ".dchip.add", "color"), ("var", "panel2")),
    ("dchip.del 差异删除",           ("rule", ".dchip.del", "color"), ("var", "panel2")),
    ("dchip.chg 差异修改",           ("rule", ".dchip.chg", "color"), ("var", "panel2")),
    ("json-status.ok",               ("rule", ".json-status.ok", "color"), ("rule", ".json-status.ok", "background")),
    ("json-status.err",              ("rule", ".json-status.err", "color"), ("rule", ".json-status.err", "background")),
    ("vtag.ok 变体标签",             ("rule", ".vtag.ok", "color"), ("rule", ".vtag.ok", "background")),
    ("vtag.err 变体标签",            ("rule", ".vtag.err", "color"), ("rule", ".vtag.err", "background")),
    ("vtag.warn 变体标签",           ("rule", ".vtag.warn", "color"), ("rule", ".vtag.warn", "background")),
    ("pill.link 关联 pill",          ("rule", ".pill.link", "color"), ("rule", ".pill.link", "background")),
    ("ctx-badge.auto 自动裁决",      ("rule", ".ctx-badge.auto", "color"), ("rule", ".ctx-badge.auto", "background")),
    ("submit-msg.ok 提交提示",       ("rule", ".submit-msg.ok", "color"), ("rule", ".submit-msg.ok", "background")),
    ("submit-msg.err 提交提示",      ("rule", ".submit-msg.err", "color"), ("rule", ".submit-msg.err", "background")),
    ("submit-msg.warn 提交提示",     ("rule", ".submit-msg.warn", "color"), ("rule", ".submit-msg.warn", "background")),
    ("lint lv.err 自检错误",         ("rule", ".lint-item .lv.err", "color"), ("rule", ".lint-item .lv.err", "background")),
    ("lint lv.warn 自检警告",        ("rule", ".lint-item .lv.warn", "color"), ("rule", ".lint-item .lv.warn", "background")),
    ("lint lv.info 自检信息",        ("rule", ".lint-item .lv.info", "color"), ("rule", ".lint-item .lv.info", "background")),
    ("notice 内联提示",              ("rule", ".notice", "color"), ("rule", ".notice", "background")),
    ("tenant-note 租户提示",         ("rule", ".tenant-note", "color"), ("rule", ".tenant-note", "background")),
    ("trace chip.warn 规则追踪",     ("rule", ".trace .chip.warn", "color"), ("rule", ".trace .chip.warn", "background")),
    ("trace chip 规则追踪",          ("var", "pri"),  ("rule", ".trace .chip", "background")),
    ("pill 标识 pill",               ("var", "pri"),  ("rule", ".pill", "background")),
    ("ctx-badge 上下文徽标",         ("var", "pri"),  ("rule", ".ctx-badge", "background")),
    ("stat .v 统计值",               ("var", "pri"),  ("var", "pri-weak")),
    ("rule-sentence .when 条件字",   ("var", "warn"), ("var", "pri-weak")),
    ("rule-sentence b 规则码",       ("var", "pri"),  ("var", "pri-weak")),
    ("fld .ro 只读徽标",             ("rule", ".fld .ro", "color"), ("rule", ".fld .ro", "background")),
    ("alert-box 警告框",             ("var", "err"),  ("rule", ".alert-box", "background")),
    ("scope-chip 行策略",            ("rule", ".scope-chip", "color"), ("rule", ".scope-chip", "background")),
    ("ov-badge 覆写徽标",            ("rule", ".ov-badge", "color"), ("rule", ".ov-badge", "background")),
    ("tag-off 停用标记",             ("rule", ".tag-off", "color"), ("rule", ".tag-off", "background")),
    ("tabs-bar 激活页签",            ("rule", ".tabs-bar .tab.active", "color"), ("rule", ".tabs-bar .tab.active", "background")),
]

for tname, tv in (("light", LIGHT), ("dark", DARK)):
    P, P2, B = tv["panel"], tv["panel2"], tv["bg"]
    # 1) 正文/次要文字
    add(tname, "正文 ink/panel", tv["ink"], P, 4.5)
    add(tname, "正文 ink/panel2", tv["ink"], P2, 4.5)
    add(tname, "正文 ink/bg", tv["ink"], B, 4.5)
    add(tname, "次要 sub/panel", tv["sub"], P, 4.5)
    add(tname, "次要 sub/panel2", tv["sub"], P2, 4.5)
    add(tname, "次要 sub/bg", tv["sub"], B, 4.5)
    add(tname, "次要 sub/line2", tv["sub"], tv["line2"], 4.5)
    # 2) 状态色作文字
    for c in ("pri", "ok", "warn", "err"):
        add(tname, f"{c}/panel", tv[c], P, 4.5)
        add(tname, f"{c}/panel2", tv[c], P2, 4.5)
    add(tname, "pri/pri-weak", tv["pri"], tv["pri-weak"], 4.5)
    # 白字只落在浅色主题的实色 pri 上（激活 tab / 图表条深端）；深色主题白字面一律走 --grad（停靠已按 4.5+ 求解）
    if tname == "light":
        add(tname, "白字/pri（激活 tab）", "#ffffff", tv["pri"], 4.5)
    # 3) 渐变停靠白字（--grad：logo/.btn.pri；.actionbar button.primary：pri→pri2 或深色 --grad）
    for i, stop in enumerate(grad_stops(tv)):
        add(tname, f"白字/--grad 停靠点{i+1}（logo/主钮）", "#ffffff", "#%02x%02x%02x" % stop, 4.5)
    for i, stop in enumerate(rule_grad_stops(".actionbar button.primary", "background", tname)):
        add(tname, f"白字/动作条停靠点{i+1}", "#ffffff", "#%02x%02x%02x" % stop, 4.5)
    # 4) 徽标/标签族：文字 on 同色相 tint（叠 panel）
    for name, fg_spec, bg_spec in TINT_MAP:
        if fg_spec[0] == "var":
            fg = tv[fg_spec[1]]
        else:
            fg = pick(fg_spec[1], fg_spec[2], tname)
        if bg_spec[0] == "var":
            bg = tv[bg_spec[1]]
        else:
            bg = pick(bg_spec[1], bg_spec[2], tname)
        add_tint(tname, name, fg, bg, P)
    # 5) 图形元素 3:1（圆点/时间线点/章节左边框，均坐于 panel）
    add(tname, "diff 圆点 add（图形）", tv["ok"], P, 3.0, "graphic")
    add(tname, "diff 圆点 del（图形）", tv["err"], P, 3.0, "graphic")
    add(tname, "diff 圆点 mod（图形）", tv["warn"], P, 3.0, "graphic")
    add(tname, "时间线点（图形）", tv["pri"], P, 3.0, "graphic")
    add(tname, "章节左边框（图形）", tv["pri"], P, 3.0, "graphic")
    add(tname, "草稿点（图形）", tv["ok"], P, 3.0, "graphic")
    add(tname, "脏记号点（图形）", tv["warn"], P, 3.0, "graphic")

# 图表条数值标签：白字贴在渐变深端（浅色 pri 端 / 深色翻转后 pri2 端）
add("light", "图表条标签白字/pri（深端）", "#ffffff", LIGHT["pri"], 4.5)
add("dark", "图表条标签白字/pri2（深端）", "#ffffff", DARK["pri2"], 4.5)

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
