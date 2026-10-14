#!/usr/bin/env node
/**
 * BONE 业务对象界面设计器 · 实体目录绑定门禁
 *
 * 验证「从实体生成 / 绑定字段」扩展能力：
 *   1. doGenerate 在三个样板（报销 / 采购 / 理赔）上生成的 schema
 *      —— 仍通过 门禁1 协议机校 / 门禁2 语义自检 / 门禁3 机器结构（零意外漂移）
 *   2. bindEntityField 能把控件正确映射到实体字段类型（DECIMAL→MONEY 等）
 *   3. controlFromEntityField 类型映射全覆盖、无遗漏
 *
 * 用法：node validate-entity-binding.js
 * 退出码 0 = 全部通过；否则 1。
 */
"use strict";
const fs = require("fs");
const path = require("path");

const DIR = __dirname;
const DESIGNER = path.join(DIR, "bone-ui-schema-designer.html");
const SCHEMA_FILE = path.join(DIR, "bone-ui-schema.schema.json");
const clone = (o) => JSON.parse(JSON.stringify(o));
let FAILS = 0;

function check(name, cond, detail) {
  if (cond) console.log("    ✓ " + name);
  else { console.log("    ✕ " + name + (detail ? " —— " + detail : "")); FAILS++; }
}

/* ---------- 从设计器取引擎（不执行 boot） ---------- */
function extract() {
  const html = fs.readFileSync(DESIGNER, "utf8");
  const m = html.match(/<script>([\s\S]*)<\/script>/);
  if (!m) throw new Error("设计器中未找到 <script> 块");
  let src = m[1].replace(/\nboot\(\);\s*$/, "\n");
  const mkEl = () => {
    const n = {
      children: [], style: {}, dataset: {}, value: "", textContent: "",
      _cls: "", _html: "",
      classList: { add() {}, remove() {}, contains: () => false, toggle() {} },
      append(c) { if (c) this.children.push(c); }, prepend(c) { if (c) this.children.unshift(c); }, addEventListener() {}, setAttribute() {},
      remove() {}, focus() {}, click() {}, querySelector: () => null, querySelectorAll: () => [],
      getBoundingClientRect: () => ({ left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }),
      get parentElement() { return null; }, offsetWidth: 0, offsetHeight: 0,
    };
    Object.defineProperty(n, "className", { get() { return this._cls; }, set(v) { this._cls = v; } });
    Object.defineProperty(n, "innerHTML", { get() { return this._html; }, set(v) { this._html = v; this.children = []; } });
    return n;
  };
  global.document = {
    createElement: mkEl, createTextNode: (t) => ({ nodeType: 3, textContent: String(t) }),
    querySelector: () => mkEl(), querySelectorAll: () => [], addEventListener() {},
    getElementById: () => mkEl(), body: mkEl(),
    activeElement: null, documentElement: { getAttribute: () => null, setAttribute() {} },
  };
  global.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
  try { global.navigator = { clipboard: { writeText: () => Promise.resolve() } }; } catch (e) {}
  global.window = { innerWidth: 1280, innerHeight: 800, addEventListener() {}, removeEventListener() {} };
  src += "\n;return {SAMPLES, lintSchema, validateMachine, validateAgainstSchema, state, clone, ENTITY_CATALOG, doGenerate, bindEntityField, entityFieldControl, entityFieldDef, controlFromEntityField};";
  return new Function(src)();
}

/* ---------- 最小 JSON Schema 校验器（与协议真源对齐） ---------- */
function typeOk(v, t) {
  switch (t) {
    case "object": return v && typeof v === "object" && !Array.isArray(v);
    case "array": return Array.isArray(v);
    case "string": return typeof v === "string";
    case "number": return typeof v === "number";
    case "integer": return typeof v === "number" && Number.isInteger(v);
    case "boolean": return typeof v === "boolean";
    default: return true;
  }
}
function makeValidator(schema) {
  function walk(node, sch, p, out) {
    if (!sch) return;
    if (sch.$ref) { walk(node, schema.definitions[sch.$ref.replace(/^#\/definitions\//, "")], p, out); return; }
    if (sch.type && !typeOk(node, sch.type)) { out.push({ p, why: "类型应为 " + sch.type }); return; }
    if (sch.enum && node !== undefined && !sch.enum.includes(node)) out.push({ p, why: "值不在枚举 " + JSON.stringify(sch.enum) });
    if (sch.type === "object" && node && typeof node === "object" && !Array.isArray(node)) {
      (sch.required || []).forEach((k) => { if (!(k in node)) out.push({ p: p + "." + k, why: "缺少必填" }); });
      const props = sch.properties || {};
      Object.keys(node).forEach((k) => {
        if (props[k]) walk(node[k], props[k], p + "." + k, out);
        else if (sch.additionalProperties === false) out.push({ p: p + "." + k, why: "协议未定义（additionalProperties=false）", x: k.startsWith("x_") });
      });
    }
    if (sch.type === "array" && Array.isArray(node) && sch.items) node.forEach((it, i) => walk(it, sch.items, p + "[" + i + "]", out));
  }
  return (inst) => { const out = []; walk(inst, schema, "$", out); return out; };
}
const isExempt = (e) => e.x || /props\.optionsRef$/.test(e.p);

const api = extract();
const schemaJson = JSON.parse(fs.readFileSync(SCHEMA_FILE, "utf8"));
const validate = makeValidator(schemaJson);

console.log("校验目标：" + path.basename(DESIGNER) + "（实体目录绑定扩展）\n");

/* ---------- 1. doGenerate 三场景：生成后仍零漂移 ---------- */
const pick = { apply_no: true, amount: true, applicant_id: true, reason: true, attachment: true };
["expense", "procurement", "claim"].forEach((id) => {
  console.log("--- 实体生成 · " + id + "：" + api.SAMPLES[id].name + " ---");
  api.state.schema = clone(api.SAMPLES[id].schema);
  api.state.schema.views.list = api.state.schema.views.list || { columns: [] };
  const tgs = { form: { checked: true }, detail: { checked: true }, list: { checked: true } };
  api.doGenerate(pick, tgs);
  // 门禁1 协议机校
  const against = validate(api.state.schema).filter((e) => !isExempt(e));
  check("门禁1 协议机校：生成后无意外漂移", against.length === 0, against.map((e) => e.p + ":" + e.why).join("; "));
  // 门禁2 语义自检
  const lint = api.lintSchema().filter((i) => i.lv === "err");
  check("门禁2 语义自检：生成后 0 错误", lint.length === 0, lint.map((i) => i.msg).join("; "));
  // 门禁3 机器结构
  const mach = api.validateMachine();
  check("门禁3 机器结构：生成后通过", mach.length === 0, mach.join("; "));
  // 生成确实生效（dedup 后可能跳过已存在字段，故按「合计新增 ≥1」判定，不卡死精确数）
  const formNew = (api.state.schema.views.form.sections || []).filter((s) => (s.title || "").startsWith("实体字段")).reduce((a, s) => a + (s.controls || []).length, 0);
  const lc = (api.state.schema.views.list.columns || []).filter((c) => pick[c.fieldCode]).length;
  check("生成落点：表单/列表合计新增 ≥1 字段", formNew + lc >= 1, "form新增=" + formNew + " list新增=" + lc);
});

/* ---------- 2. bindEntityField 类型映射正确 ---------- */
console.log("\n--- 字段绑定 · 类型映射 ---");
api.state.schema = clone(api.SAMPLES.expense.schema);
const c = api.state.schema.views.form.sections[0].controls[0];
api.bindEntityField(c, "amount");
check("绑定 amount(DECIMAL) → MONEY", c.controlKind === "MONEY", "got " + c.controlKind);
check("绑定后 labelOverride=申报金额", c.labelOverride === "申报金额", c.labelOverride);
const c2 = api.state.schema.views.form.sections[0].controls[1] || { controlKind: "" };
api.bindEntityField(c2, "applicant_id");
check("绑定 applicant_id(REF) → REFERENCE", c2.controlKind === "REFERENCE", "got " + c2.controlKind);
check("绑定 REF 带 targetEntity", c2.props && c2.props.targetEntity === "t_user", JSON.stringify(c2.props));

/* ---------- 3. controlFromEntityField 覆盖所有类型 ---------- */
console.log("\n--- 类型映射全覆盖 ---");
const allTypes = [
  { code: "a", type: "VARCHAR" }, { code: "b", type: "TEXT" }, { code: "c", type: "INT" },
  { code: "d", type: "DECIMAL" }, { code: "e", type: "DATE" }, { code: "f", type: "BOOL" },
  { code: "g", type: "ENUM" }, { code: "h", type: "REF", ref: "t_x" }, { code: "i", type: "CHILD", child: "t_y" }, { code: "j", type: "ATTACH" }
];
const want = { VARCHAR: "INPUT", TEXT: "TEXTAREA", INT: "NUMBER", DECIMAL: "MONEY", DATE: "DATE", BOOL: "SWITCH", ENUM: "ENUM", REF: "REFERENCE", CHILD: "CHILD_TABLE", ATTACH: "ATTACHMENT" };
let bad = [];
allTypes.forEach((f) => { const got = api.controlFromEntityField(f).controlKind; if (got !== want[f.type]) bad.push(f.type + "→" + got); });
check("映射覆盖 10 种字段类型且正确", bad.length === 0, bad.join(","));

console.log("\n========================================");
console.log("实体目录绑定门禁失败：" + FAILS + " 项");
if (FAILS === 0) { console.log("PASS：实体生成 / 绑定 均产出协议合规 schema，且类型映射正确"); process.exit(0); }
else { console.log("FAIL"); process.exit(1); }
