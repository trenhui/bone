#!/usr/bin/env node
/**
 * BONE 业务对象界面设计器 · 三道门禁 + 运行时联调探针
 *
 * 作用：把 bone-ui-schema-designer.html 内置的三套样板（费用报销 / 采购审查 / 医疗理赔）
 * 与引擎函数一并取出（不执行 boot），做四重验证：
 *   门禁 1：协议真源机校   validateAgainstSchema（内嵌 schema.json v1.2）
 *   门禁 2：语义自检 lint  lintSchema（重复字段 / props 白名单 / 规则引用 / 字典 / 变体 / $变量）
 *   门禁 3：机器结构校验   validateMachine（类型 / 枚举 / 必填 / 未知键）
 *   联调 4：运行时探针     applyLinkage / runSubmitRules / resolveVariant
 *                        —— 正向命中 + 负向不命中，证明规则「有牙齿」而非假绿
 *
 * 用法：node validate-designer-gates.js
 * 退出码 0 = 三道门禁无意外漂移 + 运行时探针全绿；否则 1。
 *
 * 说明：本脚本只实现协议用到的 JSON Schema 关键字（type/required/properties/
 * additionalProperties/enum/items/$ref），不依赖 ajv，可在任意环境直接跑。
 */
"use strict";
const fs = require("fs");
const path = require("path");

const DIR = __dirname;
const DESIGNER = path.join(DIR, "bone-ui-schema-designer.html");
const SCHEMA_FILE = path.join(DIR, "bone-ui-schema.schema.json");

/* ---------- 1. 从设计器 HTML 取 SAMPLES + 引擎函数（不执行 boot，只取数据/函数） ---------- */
function extract() {
  const html = fs.readFileSync(DESIGNER, "utf8");
  const m = html.match(/<script>([\s\S]*)<\/script>/);
  if (!m) throw new Error("设计器中未找到 <script> 块");
  let src = m[1].replace(/\nboot\(\);\s*$/, "\n");
  // 最小 DOM 桩：SAMPLES 是纯数据；引擎函数仅按需读 state，不碰真实 DOM
  const mkEl = () => {
    const n = {
      children: [], style: {}, dataset: {}, value: "", textContent: "",
      classList: { add() {}, remove() {}, contains: () => false, toggle() {} },
      append() {}, addEventListener() {}, setAttribute() {}, remove() {},
      focus() {}, click() {}, querySelector: () => null, querySelectorAll: () => [],
      getBoundingClientRect: () => ({ left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }),
      offsetWidth: 0, offsetHeight: 0,
    };
    Object.defineProperty(n, "className", { get: () => "", set() {} });
    Object.defineProperty(n, "innerHTML", { get: () => "", set() {} });
    return n;
  };
  global.document = {
    createElement: mkEl, createTextNode: (t) => ({ nodeType: 3, textContent: String(t) }),
    querySelector: mkEl, querySelectorAll: () => [], addEventListener() {},
    getElementById: () => null,
    activeElement: null, documentElement: { getAttribute: () => null, setAttribute() {} },
  };
  global.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
  try { global.navigator = { clipboard: { writeText: () => Promise.resolve() } }; } catch (e) {}
  global.window = {};
  src += "\n;return {SAMPLES, lintSchema, validateMachine, validateAgainstSchema, applyLinkage, runSubmitRules, resolveVariant, evalCondition, getVal, state, MOCK, CONTROLS};";
  return new Function(src)();
}

/* ---------- 2. 最小 JSON Schema 校验器（与协议真源对齐） ---------- */
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
    if (sch.enum && node !== undefined && !sch.enum.includes(node)) {
      out.push({ p, why: "值 " + JSON.stringify(node) + " 不在枚举 " + JSON.stringify(sch.enum) });
    }
    if (sch.type === "object" && node && typeof node === "object" && !Array.isArray(node)) {
      (sch.required || []).forEach((k) => { if (!(k in node)) out.push({ p: p + "." + k, why: "缺少必填" }); });
      const props = sch.properties || {};
      Object.keys(node).forEach((k) => {
        if (props[k]) walk(node[k], props[k], p + "." + k, out);
        else if (sch.additionalProperties === false) out.push({ p: p + "." + k, why: "协议未定义（additionalProperties=false）", x: k.startsWith("x_") });
      });
    }
    if (sch.type === "array" && Array.isArray(node) && sch.items) {
      node.forEach((it, i) => walk(it, sch.items, p + "[" + i + "]", out));
    }
  }
  return (inst) => { const out = []; walk(inst, schema, "$", out); return out; };
}

/* ---------- 3. 解析内嵌 schema.json（协议真源） ---------- */
function parseEmbed() {
  const html = fs.readFileSync(DESIGNER, "utf8");
  const m = html.match(/<script type="application\/json" id="schemaJsonEmbed">([\s\S]*?)<\/script>/);
  if (!m) throw new Error("设计器未内嵌 schemaJsonEmbed");
  return JSON.parse(m[1]);
}

/* ---------- 4. 运行时联调探针 ---------- */
const clone = (o) => JSON.parse(JSON.stringify(o));
let FAILS = 0;
function check(name, cond, detail) {
  if (cond) console.log("    ✓ " + name);
  else { console.log("    ✕ " + name + (detail ? " —— " + detail : "")); FAILS++; }
}
function setScenario(api, id) {
  const s = api.SAMPLES[id];
  api.state.schema = clone(s.schema);
  api.state.values = clone((s.seed && s.seed.values) || {});
  api.state.childTables = clone((s.seed && s.seed.childTables) || {});
  api.state.attachments = clone((s.seed && s.seed.attachments) || {});
  // 运行时：子表编辑后同时镜像到 state.values[key]（设计器 renderChildTable 的 change 处理）
  for (const k in api.state.childTables) api.state.values[k] = api.state.childTables[k];
  api.state.variantKey = "";
  api.state.ctx.roles = ["reviewer", "admin"];
}

function probeExpense(api) {
  console.log("\n--- 联调探针 · 费用报销（报销场景） ---");
  setScenario(api, "expense");
  // 正向：种子值 专票 + 金额 6200 → LINK-1 / LINK-3 应命中
  api.applyLinkage();
  const fired = api.state._fired.map((f) => f.code);
  check("联动 LINK-1 专票→税号必填 命中", fired.includes("LINK-1"), "fired=" + fired.join(","));
  check("联动 LINK-3 大额→审批人必填 命中", fired.includes("LINK-3"), "fired=" + fired.join(","));
  // 正向：明细合计 6200 == 申报金额 6200 → 提交校验应通过
  let msgs = api.runSubmitRules();
  check("提交 SUB-1 明细合计一致 通过（无 ERROR）", !msgs.some((m) => m.code === "SUB-1" && m.level === "ERROR"),
    msgs.filter((m) => m.level === "ERROR").map((m) => m.code).join(","));
  // 负向：篡改明细单行使合计 != 申报金额 → SUB-1 必须报错（证明规则有牙齿）
  api.state.values.items[0].amount = "9999.00";
  msgs = api.runSubmitRules();
  check("提交 SUB-1 负向探针：合计不一致 触发 ERROR", msgs.some((m) => m.code === "SUB-1" && m.level === "ERROR"),
    "未触发，规则形同虚设");
  api.state.values.items[0].amount = "2800.00"; // 复位
  // 正向：变体自动裁决（ctx 含 approver/admin）→ 命中 approver 变体
  api.state.ctx.roles = ["approver", "admin"];
  const v = api.resolveVariant();
  check("变体自动裁决 → approver（审批工作台）", v && v.key === "approver", v ? v.key : "null");
}

function probeClaim(api) {
  console.log("\n--- 联调探针 · 医疗理赔（智能理赔场景） ---");
  setScenario(api, "claim");
  // 正向：金额 12800 ≥ 10000 → LINK-DUAL-01 命中
  api.applyLinkage();
  const fired = api.state._fired.map((f) => f.code);
  check("联动 LINK-DUAL-01 大额双复核 命中", fired.includes("LINK-DUAL-01"), "fired=" + fired.join(","));
  // 正向：ctx 含 reviewer → 自动裁决到 reviewer 变体
  api.state.ctx.roles = ["reviewer", "admin"];
  const v = api.resolveVariant();
  check("变体自动裁决 → reviewer（核赔工作台）", v && v.key === "reviewer", v ? v.key : "null");
  // 负向：金额降到 5000（<10000）→ LINK-DUAL-01 不命中
  api.state.values.amount = 5000;
  api.applyLinkage();
  check("联动 LINK-DUAL-01 负向探针：金额<1万 不命中", !api.state._fired.some((f) => f.code === "LINK-DUAL-01"));
  api.state.values.amount = 12800; // 复位
}

function probeProcurement(api) {
  console.log("\n--- 联调探针 · 采购审查（采购场景） ---");
  setScenario(api, "procurement");
  // 正向：风险等级 HIGH → LINK-R1 命中（理由必填）
  api.applyLinkage();
  const fired = api.state._fired.map((f) => f.code);
  check("联动 LINK-R1 高风险→理由必填 命中", fired.includes("LINK-R1"), "fired=" + fired.join(","));
  // 正向：ctx 含 auditor → 自动裁决到 auditor 变体
  api.state.ctx.roles = ["auditor", "admin"];
  const v = api.resolveVariant();
  check("变体自动裁决 → auditor（审计复核）", v && v.key === "auditor", v ? v.key : "null");
  // 负向：结案但理由为空 → SUB-R1 completeness 必须报错
  api.state.values.decision = "ACCEPT";
  api.state.values.reason = "";
  let msgs = api.runSubmitRules();
  check("提交 SUB-R1 负向探针：理由为空 触发 ERROR", msgs.some((m) => m.code === "SUB-R1" && m.level === "ERROR"),
    "未触发，规则形同虚设");
  // 正向：补齐理由 → 通过
  api.state.values.reason = "价格偏离率超 12%";
  msgs = api.runSubmitRules();
  check("提交 SUB-R1 理由补齐 通过（无 ERROR）", !msgs.some((m) => m.code === "SUB-R1" && m.level === "ERROR"));
}

function probePurchase(api) {
  console.log("\n--- 联调探针 · 采购订单（企业采购场景：行项目 / 预算 / 三单匹配） ---");
  setScenario(api, "purchase");
  // 正向：种子态 收货/发票 = 26000 且 status=RECEIVED → SUB-MATCH-01/02 门控命中且相符
  let msgs = api.runSubmitRules();
  check("提交 SUB-MATCH-01/02 三单相符（付款前置）通过",
    !msgs.some((m) => (m.code === "SUB-MATCH-01" || m.code === "SUB-MATCH-02") && m.level === "ERROR"),
    msgs.filter((m) => m.level === "ERROR").map((m) => m.code).join(","));
  // 正向：行项目合计 26000 = 采购金额 → SUB-ITEMS-01 通过；税额合计 3380 → SUB-TAX-01 通过
  check("提交 SUB-ITEMS-01 行项目合计一致 通过",
    !msgs.some((m) => m.code === "SUB-ITEMS-01" && m.level === "ERROR"));
  check("提交 SUB-TAX-01 税额合计一致 通过",
    !msgs.some((m) => m.code === "SUB-TAX-01" && m.level === "ERROR"));
  // 联动：received_amount=26000 > 0 → LINK-RECV-01 命中（发票号必填）
  api.applyLinkage();
  const fired = api.state._fired.map((f) => f.code);
  check("联动 LINK-RECV-01 已收货→发票号必填 命中", fired.includes("LINK-RECV-01"), "fired=" + fired.join(","));
  // 联动：NET30 → LINK-PREPAY-01 不命中（负向）
  check("联动 LINK-PREPAY-01 负向探针：NET30 不命中预付款规则",
    !api.state._fired.some((f) => f.code === "LINK-PREPAY-01"));
  // 负向：预付款缺合同号 → LINK-PREPAY-01 命中
  api.state.values.payment_terms = "PREPAY";
  api.state.values.contract_no = "";
  api.applyLinkage();
  check("联动 LINK-PREPAY-01 预付款缺合同 命中",
    api.state._fired.some((f) => f.code === "LINK-PREPAY-01"));
  api.state.values.payment_terms = "NET30";
  api.state.values.contract_no = "CT-2026-0712";
  // 负向：篡改行项目首行金额 → SUB-ITEMS-01 必须报错（证明规则有牙齿）
  api.state.values.items[0].amount = 12001;
  msgs = api.runSubmitRules();
  check("提交 SUB-ITEMS-01 负向探针：合计不一致 触发 ERROR",
    msgs.some((m) => m.code === "SUB-ITEMS-01" && m.level === "ERROR"), "未触发，规则形同虚设");
  api.state.values.items[0].amount = 12000;
  // 负向：超预算（预算降到 10000）→ SUB-BUDGET-01 + LINK-BUDGET-01 必填说明
  api.state.values.budget_amount = 10000;
  api.applyLinkage();
  check("联动 LINK-BUDGET-01 超预算→说明必填 命中",
    api.state._fired.some((f) => f.code === "LINK-BUDGET-01"));
  msgs = api.runSubmitRules();
  check("提交 SUB-BUDGET-01 负向探针：超预算 触发 ERROR",
    msgs.some((m) => m.code === "SUB-BUDGET-01" && m.level === "ERROR"), "未触发，规则形同虚设");
  api.state.values.budget_amount = 50000;
  // 负向：三单不匹配（发票金额 -1000）→ SUB-MATCH-02 拦截付款
  api.state.values.invoiced_amount = 25000;
  msgs = api.runSubmitRules();
  check("提交 SUB-MATCH-02 负向探针：发票≠订单 触发 ERROR（拦截付款）",
    msgs.some((m) => m.code === "SUB-MATCH-02" && m.level === "ERROR"), "未触发，付款闸门形同虚设");
  api.state.values.invoiced_amount = 26000;
  // 变体：ctx 含 finance → 财务应付视角
  api.state.ctx.roles = ["finance", "admin"];
  const v = api.resolveVariant();
  check("变体自动裁决 → finance（财务应付视角）", v && v.key === "finance", v ? v.key : "null");
}

/* ---------- 5. 执行 ---------- */
const isExempt = (e) => (e.x) || /props\.optionsRef$/.test(e.p);
const api = extract();
const schemaJson = parseEmbed();
const validate = makeValidator(schemaJson);

console.log("校验目标：" + path.basename(DESIGNER));
let unexpected = 0, expected = 0;
Object.keys(api.SAMPLES).forEach((id) => {
  console.log("\n===== 门禁 · " + id + "：" + api.SAMPLES[id].name + " =====");
  const schema = clone(api.SAMPLES[id].schema);
  // 门禁 1：协议真源机校
  const against = validate(schema);
  const bad1 = against.filter((e) => !isExempt(e));
  const ok1 = against.filter(isExempt);
  bad1.forEach((e) => console.log("  ✕ 门禁1 意外漂移 " + e.p + "：" + e.why));
  if (!bad1.length) console.log("  ✓ 门禁1 协议机校：无意外漂移" + (ok1.length ? "（豁免 " + ok1.length + " 处 x_ 扩展）" : ""));
  unexpected += bad1.length; expected += ok1.length;
  // 门禁 2：语义自检
  api.state.schema = schema;
  const lint = api.lintSchema();
  const lErr = lint.filter((i) => i.lv === "err");
  const lWarn = lint.filter((i) => i.lv === "warn");
  lErr.forEach((i) => console.log("  ✕ 门禁2 语义自检 ERROR：" + i.msg));
  if (!lErr.length) console.log("  ✓ 门禁2 语义自检：0 错误" + (lWarn.length ? "（" + lWarn.length + " 警告不阻断）" : ""));
  unexpected += lErr.length;
  // 门禁 3：机器结构
  const mach = api.validateMachine();
  mach.forEach((m) => console.log("  ✕ 门禁3 机器结构：" + m));
  if (!mach.length) console.log("  ✓ 门禁3 机器结构：通过");
  unexpected += mach.length;
});

// 联调 4：运行时探针
probeExpense(api);
probePurchase(api);
probeClaim(api);
probeProcurement(api);

console.log("\n========================================");
console.log("三道门禁意外漂移：" + unexpected + " 处；运行时探针失败：" + FAILS + " 项");
if (unexpected === 0 && FAILS === 0) {
  console.log("PASS：设计器产出与协议真源一致，且三场景联调（正向命中 + 负向不命中）全绿");
  process.exit(0);
} else {
  console.log("FAIL：存在门禁漂移或联调探针失败");
  process.exit(1);
}
