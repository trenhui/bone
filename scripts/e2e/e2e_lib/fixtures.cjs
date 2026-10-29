/**
 * Node 侧生产化测试数据（scripts/e2e/e2e_lib/fixtures.cjs）
 *
 * 与 Python 侧 e2e_lib/fixtures.py 对称：同一套「可读、像生产、能串成链」的造数口径
 * （规范见 scripts/e2e/README.md §3），供 .mjs/.cjs 探针取代手写测试数据
 * （裸单号、900.00 圆整金额、张三/李四随手名、写死日期等）。
 *
 * 词表真源 = scripts/seed/uat/wordlist.json：与 gen.py、fixtures.py 同读这一份共享 JSON
 * （本文件只取其 e2e 口径键与三侧共用键），增删词条只改 JSON 一处，不再内嵌镜像、
 * 不再双向同步。
 *
 * 确定性：同 seed ⇒ 同一份名称/金额/拆分。JS 侧自实现可种子化 PRNG
 * （FNV-1a 字符串散列 + mulberry32）；JS 无法复现 Python random 流，故**不要求**
 * 跨语言同 seed 同值，只要求各自语言内部同 seed 同数据。
 *
 * 用法（方法名与 Python 同名同义；铁律：每个值只生成一次存变量，创建与断言共用）：
 *   const { dataset } = require('./e2e_lib/fixtures.cjs');   // .mjs：import { dataset } from ...
 *   const ds = dataset(STAMP);               // 同后缀 ⇒ 同数据（重复调用返回缓存实例）
 *   ds.doc_no('BX')                          // 'BX-20261028-0173'（前缀-日期-序号）
 *   ds.amount()                              // 1376.42 —— 非圆整、带业务量级
 *   ds.split(2863.5, 3)                      // Σ 恒 == 2863.5 的明细拆分（分位精确守恒）
 *   ds.split(2863.5, 3, true)                // Σ == 2863.5 + 1.01（422 负向专用）
 *   ds.person() / ds.company() / ds.sku() / ds.tax_no() / ds.equipment()
 *   ds.city / ds.dept                        // 属性（构造期定死），其余生成器是方法
 *   ds.days_ago(7)                           // '2026-10-21'（运行日倒推的业务日期）
 *   ds.customer / ds.supplier                // 串链身份 {code, name}（主表/子表/动作共用）
 *
 * 本模块被 require/import 时不产生任何输出与网络请求（与 client.cjs 同约定）。
 */
'use strict';

// ---- 词表：单一真源 scripts/seed/uat/wordlist.json（gen.py / fixtures.py / fixtures.cjs
// 同读一份，增删词条只改该 JSON）。require 的相对路径以本模块文件为基准解析（本文件在
// scripts/e2e/e2e_lib → ../../seed/uat），不依赖运行 cwd。----
const WL = require('../../seed/uat/wordlist.json');

const SURNAMES = WL.surname.map((p) => p[0]);
const GIVEN = WL.given_e2e.slice();
const CITY_WORD = WL.city_word_e2e.slice();
const COMPANY_SUFFIX = WL.company_suffix.slice();
const SUPPLIER_SUFFIX = WL.supplier_suffix.slice();
const CITIES = WL.cities.slice();
const DEPTS = WL.depts.slice();

// 品类 → [系列名, 规格档位, 价格区间]——取 gen.py product_cat 的 e2e 精简口径
const PRODUCTS = WL.products_e2e.map(([name, tiers, price]) => [name, tiers.slice(), price.slice()]);
const CHANNELS = WL.channels.map((c) => c.slice(0, 2));
const WAREHOUSES = WL.warehouses.map((w) => w.slice());

// 设备资产：类别码用于生成 EQ-SMT-0347 式可读设备编号
const EQUIPMENT = WL.equipment_e2e.map((e) => e.slice());

// 统一社会信用代码假名用的固定主体段（纯虚构、不指向任何真实企业）
const TAX_BODY = '91310000MA1FL0';
const TAX_TAIL_CHARS = '0123456789ABCDEFGHJKLMNPQRTUWXY';

// ---------- 确定性 PRNG（FNV-1a 散列种子 + mulberry32；与 Python random 流不互通） ----------
/** 字符串 → 32 位无符号种子（FNV-1a） */
function hashSeed(str) {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}
/** mulberry32：返回取 [0,1) 的下一个伪随机数 */
function mulberry32(a) {
  return function () {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 保留两位小数（Python round(x, 2) 的 JS 口径；跨语言不要求逐位一致） */
const round2 = (x) => Math.round(x * 100) / 100;
const pad4 = (n) => String(n).padStart(4, '0');
const pad2 = (n) => String(n).padStart(2, '0');

/**
 * 一份按 seed 确定的生产化数据集（名称/金额/拆分恒定，日期随运行日老化）。
 * 与 fixtures.py 的 Dataset 同名同义；跨单据共享的身份在构造期一次性挂到实例上，
 * 主表、子表、动作入参引用同一对象 ⇒ 天然串成一条链。
 */
class Dataset {
  constructor(seed) {
    this.seed = String(seed);
    this._next = mulberry32(hashSeed('bone-e2e:' + this.seed));
    // 单号序号起点：取 seed 中的数字位逐位取模（与 Python int(digits) % 9000 同口径，
    // 逐位取模避免超长数字串超出 Number 精度）
    const digits = (this.seed.match(/\d/g) || []).join('');
    let m = 0;
    for (const c of digits) m = (m * 10 + (c.charCodeAt(0) - 48)) % 9000;
    this._seq = m + 1000;
    this.today = new Date();
    // 跨单据共享的业务身份：主表/子表/动作入参都引用它们 ⇒ 链路可串
    this.customer = this._identity('CUST');
    this.supplier = this._identity('SUP', null, SUPPLIER_SUFFIX);
    const ch = this._pick(CHANNELS);
    this.channel_code = ch[0];
    this.channel_name = ch[1];
    const wh = this._pick(WAREHOUSES);
    this.warehouse_code = wh[0];
    this.warehouse_name = wh[1];
    this.city = this._pick(CITIES);
    this.employee = this.person();
    this.manager = this.person(); // 第二个人（SoD 审批人/复核人）
    this.dept = this._pick(DEPTS);
  }

  // ---------- 随机原语 ----------
  /** [0,1) 均匀随机 */
  _rand() {
    return this._next();
  }
  /** [lo,hi) 均匀分布 */
  _uniform(lo, hi) {
    return lo + (hi - lo) * this._next();
  }
  /** 从数组中等概率取一项 */
  _pick(arr) {
    return arr[Math.floor(this._next() * arr.length)];
  }
  /** [lo,hi] 闭区间整数 */
  _int(lo, hi) {
    return lo + Math.floor(this._next() * (hi - lo + 1));
  }

  // ---------------- 身份与名称 ----------------
  /** 业务身份：{code, name}，编码在数据集内唯一且稳定 */
  _identity(prefix, wordPool = null, suffixPool = null) {
    const word = this._pick(wordPool || CITY_WORD);
    const suf = this._pick(suffixPool || COMPANY_SUFFIX);
    const code = `${prefix}-${pad4(this._int(1, 9999))}`;
    return { code, name: word + suf };
  }
  /** 受控词表人名（姓 + 名，无 UUID、无 test123） */
  person() {
    return this._pick(SURNAMES) + this._pick(GIVEN);
  }
  /** 受控词表公司名（词根 + 后缀） */
  company() {
    return this._pick(CITY_WORD) + this._pick(COMPANY_SUFFIX);
  }
  // 注：城市/部门是**实例属性**（ds.city / ds.dept，构造期定死、数据集内恒定），
  // 与 fixtures.py 一致——调用方一律按属性取值，不存在同名方法。

  /** 商品：[名称+规格, 参考单价] —— 名称/规格/价格同源自洽 */
  sku() {
    const [name, tiers, priceRange] = this._pick(PRODUCTS);
    const tier = this._pick(tiers);
    const unit = this.amount(priceRange[0], priceRange[1]);
    return [name + ' ' + tier, unit];
  }
  /** 虚构统一社会信用代码（固定主体段 + 确定性后缀） */
  tax_no() {
    let tail = '';
    for (let i = 0; i < 8; i++) tail += TAX_TAIL_CHARS[this._int(0, TAX_TAIL_CHARS.length - 1)];
    return TAX_BODY + tail;
  }
  /** 设备资产：[类别, 设备编号, 设备名称] —— EQ-SMT-0347 式可读编号 */
  equipment() {
    const [cat, code, name] = this._pick(EQUIPMENT);
    return [cat, `EQ-${code}-${pad4(this._int(1, 9999))}`, name];
  }

  // ---------------- 单号与日期 ----------------
  /** 业务单号：BX-20261028-0173（前缀-日期-确定性序号，可读可 grep） */
  doc_no(prefix) {
    this._seq += 1;
    const ymd = `${this.today.getFullYear()}${pad2(this.today.getMonth() + 1)}${pad2(this.today.getDate())}`;
    return `${prefix}-${ymd}-${pad4(this._seq % 10000)}`;
  }
  /** 运行日倒推 n 天的业务日期（数据老化，读起来像最近发生的事） */
  days_ago(n) {
    const d = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate() - n);
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
  }

  // ---------------- 金额 ----------------
  /** 非圆整金额（带分位）；整分金额打散，杜绝 140/1000 这类机器味圆整数 */
  amount(lo = 200, hi = 9000) {
    let v = round2(this._uniform(lo, hi));
    if (Math.abs(v - Math.round(v)) < 0.005) {
      // 整分 ⇒ 加个分位打散
      v = round2(v + this._pick([0.13, 0.27, 0.42, 0.58, 0.66, 0.79]));
    }
    return v;
  }

  /**
   * 把 total 拆成 n 条明细，Σ 恒等于 total（分位级精确守恒：全程按「分」整数运算，
   * 尾差全给最后一条，Σ 与 95_validate.py G2「订单总额 = Σ明细小计」同口径）。
   *
   * break_=true 时让 Σ 与 total 差 1.01，专供「明细合计≠申报 → 422」负向断言，
   * 差值固定使失败信息可预期。
   */
  split(total, n, break_ = false) {
    const cents = Math.round(Number(total) * 100);
    const weights = [];
    let s = 0;
    for (let i = 0; i < n; i++) {
      const w = this._uniform(0.5, 1.5);
      weights.push(w);
      s += w;
    }
    const parts = [];
    let acc = 0;
    for (let i = 0; i < n - 1; i++) {
      const c = Math.round((cents * weights[i]) / s);
      parts.push(c);
      acc += c;
    }
    // 权重∈[0.5,1.5] ⇒ 尾条恒 > 0（最坏 0.25·total），不设下限兜底以保精确守恒
    parts.push(cents - acc);
    if (break_) parts[0] += 101; // 差 1.01 元 = 101 分
    return parts.map((c) => c / 100);
  }
}

// ---------- 数据集缓存（同 seed 同实例，与 fixtures.py 同语义） ----------
const _CACHE = new Map();

/**
 * 取按 seed 确定的数据集（同 seed 同数据；重复调用返回缓存的同一实例）。
 * @param {string|number} seed 运行后缀（推荐探针的 STAMP）
 */
function dataset(seed) {
  const key = String(seed);
  if (!_CACHE.has(key)) _CACHE.set(key, new Dataset(key));
  return _CACHE.get(key);
}

// 统一用 `exports.x =` 导出：.cjs 可直接解构，.mjs 命名导入可被 cjs-module-lexer 静态识别
exports.dataset = dataset;
exports.Dataset = Dataset;
exports.SURNAMES = SURNAMES;
exports.GIVEN = GIVEN;
exports.CITY_WORD = CITY_WORD;
exports.COMPANY_SUFFIX = COMPANY_SUFFIX;
exports.SUPPLIER_SUFFIX = SUPPLIER_SUFFIX;
exports.CITIES = CITIES;
exports.DEPTS = DEPTS;
exports.PRODUCTS = PRODUCTS;
exports.CHANNELS = CHANNELS;
exports.WAREHOUSES = WAREHOUSES;
exports.EQUIPMENT = EQUIPMENT;
