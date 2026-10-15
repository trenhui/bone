#!/usr/bin/env node
/**
 * 设计器 ⇄ 协议一致性校验
 *
 * 作用：把 archive/bone-ui-schema-demo.html（已归档、含运行引擎 + 三场景的版本）内置的三套样板（费用报销 / 采购审查 / 医疗理赔）
 * 导出成 JSON，再用协议真源 bone-ui-schema.schema.json 做机器校验，找出「设计器能配、
 * 协议不认」的漂移。
 *
 * 用法：node doc/design/lowcode/ui/ui-protocol/validate-demo-samples.js
 * 注：designer.html 已于 2026-10-15 回退为纯 UI 骨架原型（无引擎 / 无三场景 / 无实体绑定），
 * 故本脚本重新指向归档版 archive/bone-ui-schema-demo.html（含引擎 + 三场景）作为校验对象。
 *
 * 退出码：
 *   0 = 无意外漂移（已知豁免项只提示，不算失败）
 *   1 = 出现协议未定义且不在豁免清单内的字段
 *
 * 豁免清单（与协议设计 §13.4 一致，改 §13.4 时同步改这里）：
 *   - 以 x_ 开头的演示扩展字段（如 x_cols）：不入协议，仅为 DEMO 调布局
 *   - props.optionsRef：§13.4 已关闭提案（实证 15/15 恒等于 fieldCode，属冗余；DEMO 改为按
 *     fieldCode 隐式绑定码表，当前样板 0 处使用）。此处仍豁免，仅为兼容历史 JSON
 *
 * 注意：本脚本只实现本协议用到的 JSON Schema 关键字（type/required/properties/
 * additionalProperties/enum/items/$ref），不依赖 ajv，便于在任意环境直接跑。
 */
"use strict";
const fs = require("fs");
const path = require("path");

const DIR = __dirname;
const DESIGNER = path.join(DIR, "archive/bone-ui-schema-demo.html");
const SCHEMA_FILE = path.join(DIR, "bone-ui-schema.schema.json");

/* ---------- 1. 从 DEMO 里取出 SAMPLES（不执行 boot，只取数据） ---------- */
function extractSamples() {
  const html = fs.readFileSync(DESIGNER, "utf8");
  const m = html.match(/<script>([\s\S]*)<\/script>/);
  if (!m) throw new Error("设计器中未找到 <script> 块");
  let src = m[1].replace(/\nboot\(\);\s*$/, "\n") + "\nreturn {SAMPLES};";
  // 最小 DOM 桩：SAMPLES 是纯数据，不需要真实 DOM
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
  // navigator 在 Node 18+ 是只读 getter，取不到也不影响取 SAMPLES，容错处理
  try { global.navigator = { clipboard: { writeText: () => Promise.resolve() } }; } catch (e) {}
  global.window = { innerWidth: 1280, innerHeight: 800, addEventListener() {}, removeEventListener() {} };
  return new Function(src)().SAMPLES;
}

/* ---------- 2. 最小 JSON Schema 校验器 ---------- */
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
    if (sch.$ref) {
      walk(node, schema.definitions[sch.$ref.replace(/^#\/definitions\//, "")], p, out);
      return;
    }
    if (sch.type && !typeOk(node, sch.type)) { out.push(p + "：类型应为 " + sch.type); return; }
    if (sch.enum && node !== undefined && !sch.enum.includes(node)) {
      out.push(p + "：值 " + JSON.stringify(node) + " 不在枚举 " + JSON.stringify(sch.enum));
    }
    if (sch.type === "object" && node && typeof node === "object") {
      (sch.required || []).forEach((k) => { if (!(k in node)) out.push(p + "：缺少必填 " + k); });
      const props = sch.properties || {};
      Object.keys(node).forEach((k) => {
        if (props[k]) walk(node[k], props[k], p + "." + k, out);
        else if (sch.additionalProperties === false) out.push(p + "." + k);
      });
    }
    if (sch.type === "array" && Array.isArray(node) && sch.items) {
      node.forEach((it, i) => walk(it, sch.items, p + "[" + i + "]", out));
    }
  }
  return (inst) => { const out = []; walk(inst, schema, "$", out); return out; };
}

/* ---------- 3. 执行 ---------- */
const isExempt = (p) => /(^|\.)x_[^.]*$/.test(p) || /props\.optionsRef$/.test(p);

const SAMPLES = extractSamples();
const validate = makeValidator(JSON.parse(fs.readFileSync(SCHEMA_FILE, "utf8")));

let unexpected = 0, expected = 0;
Object.keys(SAMPLES).forEach((id) => {
  const errs = validate(JSON.parse(JSON.stringify(SAMPLES[id].schema)));
  const bad = errs.filter((e) => !isExempt(e));
  const ok = errs.filter(isExempt);
  unexpected += bad.length; expected += ok.length;
  console.log("\n===== " + id + "：" + SAMPLES[id].name + " =====");
  if (bad.length) bad.forEach((e) => console.log("  ✕ 意外漂移 " + e + "（协议未定义）"));
  else console.log("  ✓ 无意外漂移");
  if (ok.length) console.log("  · 已知豁免 " + ok.length + " 处：x_ 扩展字段 / props.optionsRef（§13.4）");
});
console.log("\n合计：意外漂移 " + unexpected + " 处，已知豁免 " + expected + " 处");
if (unexpected) { console.log("FAIL：出现协议未定义且不在豁免清单内的字段"); process.exit(1); }
console.log("PASS：设计器产出与协议真源一致（豁免项已在 §13.4 登记）");
