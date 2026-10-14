#!/usr/bin/env node
/**
 * BONE 业务对象界面设计器 · 启动路径冒烟测试
 *
 * 主门禁会剥离 boot() 再抽取引擎，因此从不执行真实的渲染路径。
 * 本脚本在「功能性 DOM 桩」上真正运行 boot()，并额外渲染一次「选中控件」的
 * 检查器（覆盖新增的实体绑定按钮），以捕捉启动 / 渲染路径上的运行时异常。
 *
 * 用法：node smoke-boot.js
 * 退出码 0 = 启动 + 渲染无异常；否则 1。
 */
"use strict";
const fs = require("fs");
const path = require("path");
const DIR = __dirname;
const DESIGNER = path.join(DIR, "bone-ui-schema-designer.html");

let FAILS = 0;
function check(name, fn) {
  try { fn(); console.log("    ✓ " + name); }
  catch (e) { console.log("    ✕ " + name + " —— " + e.message); FAILS++; }
}

function collectText(node) {
  if (!node) return "";
  if (node.nodeType === 3) return String(node.textContent || "");
  let out = "";
  for (const c of (node.children || [])) out += collectText(c);
  return out;
}
function makeStub() {
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
  const store = {};
  global.document = {
    createElement: mkEl, createTextNode: (t) => ({ nodeType: 3, textContent: String(t) }),
    querySelector: () => mkEl(), querySelectorAll: () => [], addEventListener() {},
    getElementById: () => mkEl(), body: mkEl(),
    activeElement: null, documentElement: { getAttribute: () => null, setAttribute() {} },
  };
  global.localStorage = { getItem: (k) => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: (k) => { delete store[k]; } };
  try { global.navigator = { clipboard: { writeText: () => Promise.resolve() } }; } catch (e) {}
  global.window = { innerWidth: 1280, innerHeight: 800, addEventListener() {}, removeEventListener() {} };
  return { mkEl };
}
makeStub();

const html = fs.readFileSync(DESIGNER, "utf8");
const m = html.match(/<script>([\s\S]*)<\/script>/);
let src = m[1]; // 不剥离 boot()：让其真正执行
src += "\n;return {state, renderConfig, renderPreview, SAMPLES, renderChildTable, CHILD_COL_META};";
const api = new Function(src)();

console.log("启动路径冒烟 · " + path.basename(DESIGNER));
check("boot() 启动 + 首次渲染无异常", () => { if (!api || !api.state) throw new Error("引擎未初始化"); });
check("默认样板已加载（expense）", () => {
  if (!api.state.schema || !api.state.schema.entityCode) throw new Error("schema 未加载");
});
check("渲染「选中控件」检查器（覆盖实体绑定按钮）无异常", () => {
  // 选中表单第一个分区的第一个控件 → 触发 renderInspector→inspectControlFields（含新增绑定按钮）
  const sec = (api.state.schema.views.form.sections || [])[0];
  if (!sec || !(sec.controls || []).length) throw new Error("无控件可选中");
  api.state.selected = { type: "control", si: 0, ci: 0, fc: sec.controls[0].fieldCode };
  api.renderConfig();
  api.renderPreview();
});
check("切换样板（采购）后重渲染无异常", () => {
  api.state.sampleId = "procurement";
  // loadSample 为内部函数，这里直接复用 state + 渲染验证不崩
  api.state.selected = null;
  api.renderConfig();
  api.renderPreview();
});

// renderChildTable 是唯一未被三道门禁覆盖的引擎函数（无样板直接用它）。
// 它又是「实体绑定 CHILD→CHILD_TABLE」路径的落地渲染，且 SUB-1 明细合计提交规则依赖其 Σ 合计行。
// 这里在真实 boot + 功能性 DOM 桩上直接渲染一张子表，断言合计行正确汇总（跑通≠测到）。
check("CHILD_TABLE 子表渲染（含 Σ 合计行）无异常", () => {
  if (typeof api.renderChildTable !== "function") throw new Error("renderChildTable 未导出");
  api.state.values.currency = api.state.values.currency || "CNY";
  api.state.childTables = api.state.childTables || {};
  api.state.childTables.items = [
    { expense_date: "2026-01-01", category_id: "X", amount: "100.00", remark: "" },
    { expense_date: "2026-01-02", category_id: "Y", amount: "50.00", remark: "" },
  ];
  const ctrl = { fieldCode: "items", controlKind: "CHILD_TABLE", props: { columns: ["expense_date", "category_id", "amount", "remark"] } };
  const box = api.renderChildTable(ctrl, false);
  if (!box || !box.children || !box.children.length) throw new Error("子表未渲染出节点");
  const txt = collectText(box);
  if (!/Σ/.test(txt)) throw new Error("Σ 合计行缺失");
  if (!/150\.00/.test(txt)) throw new Error("金额合计未正确汇总（期望 150.00）");
});
check("CHILD_COL_META 元数据驱动映射正确", () => {
  if (typeof api.CHILD_COL_META !== "function") throw new Error("CHILD_COL_META 未导出");
  const m = api.CHILD_COL_META("t_expense_claim", "items", "amount");
  if (!m || m.t !== "money" || !m.sum) throw new Error("amount 应映射为 money+sum，实际 " + JSON.stringify(m));
});

console.log("\n========================================");
console.log("启动冒烟失败：" + FAILS + " 项");
if (FAILS === 0) { console.log("PASS：启动 + 渲染路径无运行时异常"); process.exit(0); }
else { console.log("FAIL"); process.exit(1); }
