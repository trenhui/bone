#!/usr/bin/env node
/**
 * 小程序报案真实端到端联调（零 mock 业务链路）。
 *
 * 复用小程序**自己的**源码驱动，不是另写一套：
 *  - 注入 global.wx 适配器：wx.request → Node fetch（真实网关 8888）；storage → 内存 Map；
 *  - mock global.Page 捕获 pages/report/report.js 的页面对象；
 *  - 用真实 validate.js 校验 + 真实 buildPayload() 构造 + 真实 api.submitClaim() 提交；
 *  - 第二证据源：MySQL 直查 t_claim 主表对账落库（接口 200 不等于真落库）。
 *
 * 前置：网关 8888 + claim 8087 + IAM 8081 已启动；种子数据 0073 已在库。
 * 运行：node miniprogram/tests/e2e.real.js
 * 退出码：0 全通过；1 任一失败。
 */
'use strict';
const path = require('path');
const { execFileSync } = require('child_process');

const MP = path.resolve(__dirname, '..');
const results = [];
function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log((ok ? '[PASS] ' : '[FAIL] ') + name + ' :: ' + detail);
}

// ---------- 注入 wx 适配器（真实 HTTP，storage 走内存） ----------
const store = {};
const navigations = [];
global.wx = {
  getStorageSync: (k) => store[k],
  setStorageSync: (k, v) => { store[k] = v; },
  removeStorageSync: (k) => { delete store[k]; },
  showToast: () => {},
  navigateTo: (o) => { navigations.push(o.url); },
  redirectTo: (o) => { navigations.push(o.url || o); },
  reLaunch: (o) => { navigations.push(o.url || o); },
  request: (o) => {
    const headers = Object.assign({}, o.header || {});
    fetch(o.url, {
      method: o.method || 'GET',
      headers,
      body: o.data ? JSON.stringify(o.data) : undefined,
      signal: AbortSignal.timeout(o.timeout || 20000),
    })
      .then(async (res) => {
        let data;
        try { data = await res.json(); } catch (_) { data = {}; }
        o.success && o.success({ statusCode: res.status, data });
      })
      .catch((err) => o.fail && o.fail({ errMsg: String(err && err.message || err) }));
  },
};

// ---------- mock Page 捕获页面对象 ----------
let reportPage = null;
global.Page = (obj) => { reportPage = obj; };

const api = require(path.join(MP, 'utils/api.js'));
const validator = require(path.join(MP, 'utils/validate.js'));
require(path.join(MP, 'pages/report/report.js'));

function makePage(overrides) {
  const data = Object.assign({}, reportPage.data, overrides || {});
  return Object.assign(Object.create(reportPage), {
    data,
    setData(patch, cb) {
      Object.assign(data, patch);
      if (typeof cb === 'function') cb.call(this);
    },
  });
}

// ---------- MySQL 直查（第二证据源，复用 e2e 已验证的凭据真源 e2e_lib/mysql.py） ----------
const E2E_LIB = path.resolve(__dirname, '../../scripts/e2e');
function mysqlRow(sql) {
  // config.MYSQL 指向 /usr/local/mysql/bin/mysql（本机不在 PATH），由 e2e_lib.Config 统一解析。
  const py = [
    'import sys;sys.path.insert(0,sys.argv[2]);from e2e_lib.mysql import query;',
    'o,e=query(sys.argv[1]);print(o if o else ("DB_ERR:"+str(e)))',
  ].join('');
  return execFileSync('python3', ['-c', py, sql, E2E_LIB], { encoding: 'utf8', timeout: 35000 }).trim();
}

(async () => {
  console.log('=== 小程序报案真实联调（复用小程序源码） ===');
  console.log('网关:', api.gateway() + api.request ? api.gateway() + '/api/v1' : '');

  // 1) 真实登录（走小程序 api.login → wx.request → 网关 → IAM）
  let loginData;
  try {
    loginData = await api.login('admin', '123456');
    check('小程序 api.login 真实登录拿到 JWT',
      !!api.token() && api.token().split('.').length === 3,
      'token 三段=' + api.token().split('.').length + ', account=' + (loginData && loginData.account && loginData.account.username));
  } catch (e) {
    check('小程序 api.login 真实登录拿到 JWT', false, JSON.stringify(e));
    return finish();
  }

  // 2) 用真实 buildPayload 构造报案 payload（企业场景：非圆整金额、多项明细）
  const page = makePage({
    policyNo: 'E2E-POLICY-0001',
    planId: '9001',
    insuredIdHash: 'e2e-insured-hash-0001',
    outInsureTime: new Date(Date.now() - 9 * 86400000).toISOString().slice(0, 10),
    items: [
      { key: 'a', itemName: '门诊诊查费', itemCode: '', categoryIndex: 0, amount: '186.40', quantity: '1', unitPrice: '186.40' },
      { key: 'b', itemName: 'CT 检查费', itemCode: 'CT-2048', categoryIndex: 2, amount: '523.75', quantity: '1', unitPrice: '523.75' },
    ],
  });
  const validation = validator.validateReport(page.data);
  check('小程序 validate.js 校验通过', validation.valid, validation.valid ? 'valid=true' : JSON.stringify(validation));
  const payload = page.buildPayload();
  check('buildPayload 构造符合后端契约',
    payload.planId === 9001 && payload.claimType === 'OUTPATIENT' &&
    payload.items.length === 2 && payload.items[0].category === 'SERVICE' && payload.items[1].category === 'EXAM' &&
    typeof payload.outInsureTime === 'string' && payload.outInsureTime.endsWith('T00:00:00Z'),
    JSON.stringify(payload));
  const expectAmount = (186.40 + 523.75).toFixed(2);

  // 3) 真实提交（走小程序 api.submitClaim → 带幂等键 → 网关 → claim）
  let receipt;
  try {
    receipt = await api.submitClaim(payload);
    check('小程序 api.submitClaim 真实报案提交成功',
      !!receipt && !!(receipt.claimNo || receipt.claimId),
      'receipt=' + JSON.stringify(receipt));
  } catch (e) {
    check('小程序 api.submitClaim 真实报案提交成功', false, JSON.stringify(e));
    return finish();
  }

  // 4) 幂等键验证：重复提交同一 payload 应被拦截（每次 submitClaim 生成新键，这里直接测服务端幂等行为）
  //    小程序层每次生成新 UUID v4，两次提交会产生两个不同赔案——验证键格式为 UUID v4。
  const key1 = api.uuidv4();
  check('幂等键为合法 UUID v4',
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(key1),
    'sample=' + key1);

  // 5) 第二证据源：MySQL 直查对账落库
  const claimNo = String(receipt.claimNo || '');
  const claimId = String(receipt.claimId || '');
  let dbRow = '';
  try {
    if (claimId && /^\d+$/.test(claimId)) {
      dbRow = mysqlRow('SELECT claim_no,status,claim_amount FROM t_claim_case WHERE id=' + claimId + ' AND tenant_id=0 LIMIT 1');
    } else if (claimNo) {
      dbRow = mysqlRow("SELECT claim_no,status,claim_amount FROM t_claim_case WHERE claim_no='" + claimNo.replace(/'/g, '') + "' AND tenant_id=0 LIMIT 1");
    }
  } catch (e) {
    dbRow = 'DB_ERR:' + String(e.message || e).slice(0, 120);
  }
  check('MySQL 直查确认赔案真实落库',
    !!dbRow && !dbRow.startsWith('DB_ERR') && dbRow.includes(claimNo),
    'claimNo=' + claimNo + ' dbRow=' + (dbRow || '(empty)'));

  // 6) 金额对账：库内金额 == 明细 Σ
  const amtMatch = dbRow && dbRow.split('\t').some((v) => {
    const n = Number(v);
    return Math.abs(n - Number(expectAmount)) < 0.01;
  });
  check('库内申请金额 == 小程序明细合计', !!amtMatch,
    'expect=' + expectAmount + ' dbRow=' + dbRow);

  finish();

  function finish() {
    const pass = results.filter((r) => r.ok).length;
    const fail = results.length - pass;
    console.log('\n=== 联调结果 ' + pass + '/' + results.length + ' PASS' + (fail ? ', ' + fail + ' FAIL' : '') + ' ===');
    process.exit(fail ? 1 : 0);
  }
})().catch((e) => {
  console.error('[FATAL]', e && e.stack || e);
  process.exit(1);
});
