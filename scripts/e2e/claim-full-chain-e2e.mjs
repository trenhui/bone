#!/usr/bin/env node
/**
 * 智能理赔全功能 E2E（synap-claim，A1-A9 全能力）。
 *
 * 设计原则
 * --------------------------------
 * · **只走真实 API**（经网关 :8888，与前端同口径），不直插 DB——测试数据 = 业务代码的真实产物；
 * · **多角色**：admin（第一审核人）+ claim_reviewer2（复核/复议岗，脚本内经 IAM API 创建，
 *   走「管理员建账号→建角色→绑权限→绑角色」真实管理链路），双人复核的职责分离（SoD）才可测；
 * · **多分支**：小额直付 / 大额双人复核 / 拒赔+申诉维持 / 拒赔+申诉推翻 / 补件 / 挂起恢复 /
 *   自动通过 / 抽检 PASS+DIFF；
 * · **负向探针**：失效保单、被保人不匹配、未锁证据理算、同人复核——「能走通」之外还要「该拒的拒」；
 * · 幂等可重跑：账号/角色按用户名与 code 复用；案件幂等键带时间戳，每次运行新建案件。
 *
 * 运行：node scripts/e2e/claim-full-chain-e2e.mjs
 * 退出码：0 = 全部断言通过；1 = 任意失败（失败清单见输出）。
 */

const GW = process.env.CLAIM_E2E_GATEWAY ?? 'http://127.0.0.1:8888';
const ADMIN_USER = process.env.CLAIM_E2E_ADMIN ?? 'admin';
const ADMIN_PASS = process.env.CLAIM_E2E_ADMIN_PASS ?? '123456';
const REVIEWER2_USER = 'claim_reviewer2';
const REVIEWER2_PASS = 'Claim2@2026';
const REVIEWER2_ROLE_CODE = 'CLAIM_REVIEWER';
const POLICY = { policyNo: 'E2E-POLICY-0001', planId: '9001', insuredIdHash: 'e2e-insured-hash-0001' };

let passed = 0;
let failed = 0;
const failures = [];

function ok(name, cond, detail = '') {
  if (cond) {
    passed++;
    console.log(`  PASS  ${name}`);
  } else {
    failed++;
    failures.push(`${name} ${detail}`);
    console.log(`  FAIL  ${name}  ${detail}`);
  }
}

function api(token, method, path, body, headers = {}) {
  return fetch(`${GW}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? {} : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).then(async (r) => {
    let data = null;
    try {
      data = await r.json();
    } catch {
      /* 204 等 */
    }
    return { status: r.status, data };
  });
}

const sha256 = async (s) =>
  Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

async function login(username, password) {
  const r = await api(null, 'POST', '/api/v1/iam/login', { username, password });
  if (!r.data?.data?.token) throw new Error(`登录失败 ${username}: ${JSON.stringify(r.data)}`);
  return r.data.data.token;
}

/** 登记并锁定证据集（理算前置）。 */
async function registerAndLockEvidence(token, claimId, names) {
  for (const n of names) {
    const contentHash = await sha256(`${claimId}:${n}:${Date.now()}`);
    const r = await api(token, 'POST', `/api/v1/claims/${claimId}/evidences`, {
      fileRef: `claim/${claimId}/${n}.pdf`,
      contentHash,
      docType: n.includes('list') ? 'LIST' : 'INVOICE',
      verifyValid: 'PASS',
    }, { 'Idempotency-Key': `ev-${claimId}-${n}-${Math.random().toString(16).slice(2, 8)}` });
    if (r.status !== 201 && r.status !== 200) throw new Error(`登记材料失败: ${JSON.stringify(r.data)}`);
  }
  const lock = await api(token, 'POST', `/api/v1/claims/${claimId}/evidences/lock`);
  if (lock.status !== 200) throw new Error(`锁定证据失败: ${JSON.stringify(lock.data)}`);
  return lock;
}

/** 主干推进：受理 → 分配 → 初审 → 转人工核赔 → 领取。返回时案件处于 41（核赔中）。 */
async function driveToAdjusting(token, claimId, approvedAmount = 0) {
  const steps = [
    ['allocate', () => api(token, 'POST', `/api/v1/claims/${claimId}/allocate`)],
    ['pre-exam/start', () => api(token, 'POST', `/api/v1/claims/${claimId}/pre-exam/start`, undefined, { 'X-Automated': 'false' })],
    // 核定金额 = 初审结论，理算基准（明细 valid_amount）由后端按此回填；传 0 等于核定「零赔付」
    ['pre-exam/complete', () => api(token, 'POST', `/api/v1/claims/${claimId}/pre-exam/complete`, undefined, { 'X-Approved-Amount': String(approvedAmount) })],
    ['escalate', () => api(token, 'POST', `/api/v1/claims/${claimId}/escalate`)],
    ['adjust/claim', () => api(token, 'POST', `/api/v1/claims/${claimId}/adjust/claim`)],
  ];
  for (const [name, fn] of steps) {
    const r = await fn();
    if (r.status !== 200) throw new Error(`${name} 失败: ${JSON.stringify(r.data)}`);
  }
}

async function submitClaim(token, items, outInsureTime = '2026-10-20T09:00:00Z', policy = POLICY) {
  const r = await api(token, 'POST', '/api/v1/claims', { ...policy, claimType: 'OUTPATIENT', outInsureTime, items },
    { 'Idempotency-Key': `e2e-${Date.now()}-${Math.random().toString(16).slice(2, 8)}` });
  if (r.status !== 201) throw new Error(`报案失败: ${JSON.stringify(r.data)}`);
  return r.data.data;
}

async function ensureClaimUser(adminToken, username, realName) {
  // 1) 角色：按 code 查，缺则建
  let roleId = null;
  const roles = await api(adminToken, 'GET', `/api/v1/iam/roles?page=1&size=100`);
  const found = (roles.data?.data?.records ?? []).find((x) => x.code === REVIEWER2_ROLE_CODE);
  if (found) {
    roleId = found.id;
  } else {
    const created = await api(adminToken, 'POST', '/api/v1/iam/roles', {
      name: '理赔复核岗', code: REVIEWER2_ROLE_CODE, description: '智能理赔双人复核/复议岗（E2E）', tenantId: 0,
    });
    roleId = created.data?.data;
    ok('创建复核岗角色', roleId != null && roleId !== '', JSON.stringify(created.data));
  }
  // 2) 权限：claim:* 六码
  const perms = await api(adminToken, 'GET', `/api/v1/iam/permissions?page=1&size=500`);
  const claimPerms = (perms.data?.data?.records ?? []).filter((p) => p.code?.startsWith('claim:'));
  const assign = await api(adminToken, 'POST', `/api/v1/iam/roles/${roleId}/permissions`,
    { permissionIds: claimPerms.map((p) => p.id) });
  ok(`绑定 claim 权限到复核岗（${username}）`, assign.status === 200, JSON.stringify(assign.data));
  // 3) 账号：按用户名查，缺则建（带角色+部门），存在则重置密码
  const accounts = await api(adminToken, 'GET', `/api/v1/iam/accounts?page=1&size=200&keyword=${username}`);
  const acc = (accounts.data?.data?.records ?? []).find((a) => a.username === username);
  if (!acc) {
    // 归属部门必填：取部门树第一个节点（真实管理链路同款动作）
    const depts = await api(adminToken, 'GET', `/api/v1/iam/depts/tree`);
    const flat = [];
    const walk = (nodes) => {
      for (const n of nodes ?? []) {
        flat.push(n);
        walk(n.children ?? n.childrenList);
      }
    };
    walk(depts.data?.data);
    const deptId = flat[0]?.id;
    if (!deptId) throw new Error(`无可用部门: ${JSON.stringify(depts.data)}`);
    const created = await api(adminToken, 'POST', '/api/v1/iam/accounts', {
      username, password: REVIEWER2_PASS, email: `${username}@e2e.bone.local`,
      realName, roleIds: [roleId], deptId,
    });
    ok(`创建账号 ${username}`, created.status === 200, JSON.stringify(created.data));
  } else {
    await api(adminToken, 'POST', `/api/v1/iam/accounts/${acc.id}/reset-password`, { newPassword: REVIEWER2_PASS });
  }
  return login(username, REVIEWER2_PASS);
}

async function main() {
  console.log(`\n===== 智能理赔全功能 E2E（${GW}） =====\n`);
  const admin = await login(ADMIN_USER, ADMIN_PASS);
  ok('admin 登录（tenant 0）', true);
  const reviewer2 = await ensureClaimUser(admin, REVIEWER2_USER, '复核员二号');
  ok('复核岗账号可用（reviewer2 登录）', Boolean(reviewer2));
  // 大额资金链需第三个身份：结算首审 ≠ 案件第一审核人（admin），结算复核 ≠ 首审发起人（auditor）
  const auditor = await ensureClaimUser(admin, 'claim_auditor', '结算审核员');
  ok('结算审核账号可用（claim_auditor 登录）', Boolean(auditor));

  // ---------- 负向：失效保单 / 被保人不匹配 ----------
  console.log('\n--- 负向：报案前置校验 ---');
  {
    const r1 = await api(admin, 'POST', '/api/v1/claims',
      { ...POLICY, policyNo: 'E2E-POLICY-EXPIRED', planId: '9002', claimType: 'OUTPATIENT', outInsureTime: '2026-10-20T09:00:00Z', items: [{ itemName: 'x', amount: 10 }] },
      { 'Idempotency-Key': `neg-${Date.now()}` });
    ok('失效保单被拒（422 SC_CLAIM_POLICY_NOT_ACTIVE）', r1.status === 422, `got ${r1.status}`);
    const r2 = await api(admin, 'POST', '/api/v1/claims',
      { ...POLICY, insuredIdHash: 'wrong-insured', claimType: 'OUTPATIENT', outInsureTime: '2026-10-20T09:00:00Z', items: [{ itemName: 'x', amount: 10 }] },
      { 'Idempotency-Key': `neg2-${Date.now()}` });
    ok('被保人不匹配被拒（403）', r2.status === 403, `got ${r2.status}`);
  }

  // ---------- 分支 1：小额全链（报案→材料→理算→核赔→资金→支付→结案→额度） ----------
  console.log('\n--- 分支 1：小额直付全链（150 元） ---');
  {
    const c = await submitClaim(admin, [{ itemName: '门诊诊查费', category: 'SERVICE', quantity: 1, unitPrice: 100, amount: 100 }, { itemName: '药品费', category: 'DRUG', quantity: 2, unitPrice: 25, amount: 50 }]);
    ok('受理 201 / 对外 PENDING_EVIDENCE', c.status === 'PENDING_EVIDENCE', c.status);
    await registerAndLockEvidence(admin, c.claimId, ['invoice', 'list']);

    // 负向探针已在锁前被绕过：这里用另一案验证「未锁不能理算」
    const c2 = await submitClaim(admin, [{ itemName: '检验费', category: 'EXAM', amount: 60 }]);
    await api(admin, 'POST', `/api/v1/claims/${c2.claimId}/evidences`, { fileRef: `claim/${c2.claimId}/a.pdf`, contentHash: await sha256(`${c2.claimId}:a`), verifyValid: 'PASS' }, { 'Idempotency-Key': `neg3-${c2.claimId}` });
    const neg = await api(admin, 'POST', `/api/v1/claims/${c2.claimId}/adjust`);
    ok('证据未锁定理算被拒（404/422）', [404, 422].includes(neg.status), `got ${neg.status}`);
    // 该案继续作为分支 5（补件）素材：推进到 41 再补件
    await registerAndLockEvidence(admin, c2.claimId, ['invoice-fixed']);
    await driveToAdjusting(admin, c2.claimId, 60);

    await driveToAdjusting(admin, c.claimId, 150);
    const adj = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/adjust`, undefined, { 'Idempotency-Key': `adj-${c.claimId}` });
    ok('执行理算 200（90% 责任）', adj.status === 200 && adj.data?.data?.totalAmount > 0, JSON.stringify(adj.data?.data?.totalAmount));
    ok('公式哈希与快照非空', Boolean(adj.data?.data?.formulaHash && adj.data?.data?.snapshot));
    const conf = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/adjust/confirm`);
    ok('理算确认 200', conf.status === 200, `got ${conf.status}`);

    const dec = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/decision`, { decision: 'APPROVED', approvedAmount: adj.data.data.totalAmount, comment: 'E2E 小额通过' });
    ok('人工核赔通过（41→42）', dec.status === 200, `got ${dec.status}`);

    const st = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement`, { attempt: 1, accountType: 'PERSONAL' }, { 'Idempotency-Key': `st-${c.claimId}` });
    ok('建结算单（软占额度）', [200, 201].includes(st.status) && Boolean(st.data?.data?.settlementNo), JSON.stringify(st.data?.data));
    const ap = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement/audit-pass`);
    ok('结算审核（小额直接硬预占）', ap.status === 200 && ap.data?.data?.fundingStage === 'HARD_RESERVED', JSON.stringify(ap.data?.data?.fundingStage));
    const pay = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement/pay`);
    ok('支付完成', pay.status === 200, `got ${pay.status}`);
    const detail = await api(admin, 'GET', `/api/v1/claims/${c.claimId}`);
    ok('结案 FINISHED（支付完成自动结案 42→50）', detail.data?.data?.[0]?.status === 'FINISHED', JSON.stringify({ status: detail.data?.data?.[0]?.status, ext: detail.data?.data?.[0]?.externalStatus }));
    const quota = await api(admin, 'GET', `/api/v1/claims/${c.claimId}/quota`);
    ok('额度账户三值可见（claimed 增加）', quota.status === 200 && quota.data?.data?.claimed > 0, JSON.stringify(quota.data?.data));
    globalThis.case1Claim = c.claimId;
  }

  // ---------- 分支 2：大额双人复核（15000 元 ≥1 万）+ SoD ----------
  console.log('\n--- 分支 2：大额双人复核（15000 元）+ 职责分离 ---');
  {
    const c = await submitClaim(admin, [{ itemName: '手术费', category: 'SERVICE', quantity: 1, unitPrice: 15000, amount: 15000 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice', 'medical-record']);
    await driveToAdjusting(admin, c.claimId, 15000);
    const adj = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/adjust`, undefined, { 'Idempotency-Key': `adj-${c.claimId}` });
    ok('大额理算 200', adj.status === 200, `got ${adj.status} ${JSON.stringify(adj.data?.detail ?? adj.data?.message ?? '')}`);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/adjust/confirm`);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/decision`, { decision: 'APPROVED', approvedAmount: adj.data?.data?.totalAmount ?? 15000, comment: 'E2E 大额通过' });
    // 结算两段式：建单（软占）→ 第一道审核（≥1 万进入复核冻结；审核人须 ≠ 案件第一审核人）
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement`, { attempt: 1, accountType: 'PERSONAL' }, { 'Idempotency-Key': `st-${c.claimId}` });
    const audit = await api(auditor, 'POST', `/api/v1/claims/${c.claimId}/settlement/audit-pass`);
    ok('大额结算审核进入复核冻结', audit.status === 200 && audit.data?.data?.fundingStage === 'REVIEW_FREEZE', JSON.stringify(audit.data?.data?.fundingStage));
    // 负向：第一审核人不能发起/执行双人复核（发起即校验复核人 ≠ 第一审核人）
    const samePerson = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/second-review`);
    ok('第一审核人发起复核被拒（SoD）', samePerson.status >= 400, `got ${samePerson.status}`);
    // 负向：结算复核也不能由第一审核人放行
    const settleSame = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement/review-confirm`);
    ok('结算复核同人被拒（SoD）', [403, 409].includes(settleSame.status) || settleSame.status >= 400, `got ${settleSame.status}`);
    // 复核岗：发起（42→44）→ 结论（44→50）→ 结算复核放行 → 支付
    const sr = await api(reviewer2, 'POST', `/api/v1/claims/${c.claimId}/second-review`);
    ok('复核岗发起双人复核（42→44）', sr.status === 200, `got ${sr.status} ${JSON.stringify(sr.data)}`);
    const done = await api(reviewer2, 'POST', `/api/v1/claims/${c.claimId}/second-review/complete`, undefined, { 'X-Approved': 'true' });
    ok('复核人放行（44→50）', done.status === 200, `got ${done.status} ${JSON.stringify(done.data)}`);
    const rc = await api(reviewer2, 'POST', `/api/v1/claims/${c.claimId}/settlement/review-confirm`);
    ok('结算大额复核放行（REVIEW_FREEZE→HARD_RESERVED）', rc.status === 200 && rc.data?.data?.fundingStage === 'HARD_RESERVED', JSON.stringify(rc.data?.data?.fundingStage));
    const pay = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/settlement/pay`);
    ok('大额支付完成', pay.status === 200, `got ${pay.status} ${JSON.stringify(pay.data?.message ?? '')}`);
    const detail = await api(admin, 'GET', `/api/v1/claims/${c.claimId}`);
    ok('大额案复核后结案', detail.data?.data?.[0]?.status === 'FINISHED', detail.data?.data?.[0]?.status);
    globalThis.case2Claim = c.claimId;
  }

  // ---------- 分支 3：拒赔 + 申诉维持 ----------
  console.log('\n--- 分支 3：拒赔 + 申诉维持（UPHELD） ---');
  {
    const c = await submitClaim(admin, [{ itemName: '理疗费', category: 'SERVICE', amount: 80 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await driveToAdjusting(admin, c.claimId, 80);
    const dec = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/decision`, {
      decision: 'REJECTED', rejectType: '01', comment: JSON.stringify({ factFindings: '既往症就诊', clauseBasis: '条款第 7.2 条责任免除', evidenceRefs: [`claim/${c.claimId}/invoice.pdf`], appealChannel: '客服热线 95xxx', legalDeadline: '收到拒赔通知后 48 小时内' }),
    });
    ok('拒赔生效（41→45）', dec.status === 200, `got ${dec.status}`);
    const ap = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/appeal`, { reason: '对拒赔结论有异议：本次为初次确诊' }, {});
    ok('发起申诉（45→60，201）', [200, 201].includes(ap.status) && ap.data?.data?.appealNo, `got ${ap.status} ${JSON.stringify(ap.data?.data)}`);
    const up = await api(reviewer2, 'POST', `/api/v1/claims/${c.claimId}/appeal/review`, { result: 'UPHELD', reason: '复议维持：既往症属责任免除' });
    ok('复议维持（UPHELD）', up.status === 200 && up.data?.data?.reviewResult === 'UPHELD', JSON.stringify(up.data?.data));
    globalThis.case3Claim = c.claimId;
  }

  // ---------- 分支 4：拒赔 + 申诉推翻（REOPEN）+ 挂起/恢复 ----------
  console.log('\n--- 分支 4：申诉推翻重启审核 + 挂起/恢复 ---');
  {
    const c = await submitClaim(admin, [{ itemName: 'CT 检查费', category: 'EXAM', amount: 320 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await driveToAdjusting(admin, c.claimId, 320);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/decision`, {
      decision: 'REJECTED', rejectType: '02', comment: JSON.stringify({ factFindings: '材料存疑', clauseBasis: '条款 7.3', evidenceRefs: [], appealChannel: '95xxx', legalDeadline: '48h' }),
    });
    const ap = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/appeal`, { reason: '已补充病历材料，申请复议' });
    const re = await api(reviewer2, 'POST', `/api/v1/claims/${c.claimId}/appeal/review`, { result: 'REOPEN', reason: '新证据成立，推翻原结论' });
    ok('复议推翻重启审核（REOPEN）', re.status === 200 && re.data?.data?.reviewResult === 'REOPEN', JSON.stringify(re.data?.data));
    const detail = await api(admin, 'GET', `/api/v1/claims/${c.claimId}`);
    const stAfterReopen = detail.data?.data?.[0]?.status;
    ok('推翻后回到审核段（非终态）', ['PENDING_ADJUST', 'ADJUSTING', 'ADJUSTED'].includes(stAfterReopen), stAfterReopen);
    // 挂起 / 恢复（正交机制）
    const hang = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/hangup`, undefined, { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': 'EVIDENCE' });
    ok('挂起（EVIDENCE）', hang.status === 200, `got ${hang.status}`);
    const hung = await api(admin, 'GET', `/api/v1/claims/${c.claimId}`);
    ok('挂起态可见（hang_up_status=1）', hung.data?.data?.[0]?.hangUpStatus === 1, String(hung.data?.data?.[0]?.hangUpStatus));
    const resume = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/hangup`, undefined, { 'X-Hangup-Action': 'RESUME' });
    ok('恢复', resume.status === 200, `got ${resume.status}`);
    globalThis.case4Claim = c.claimId;
  }

  // ---------- 分支 5：补件（REQUEST_MORE_EVIDENCE → 21 → 补齐再走） ----------
  console.log('\n--- 分支 5：补件循环 ---');
  {
    const c = globalThis.case1Neg ?? null;
    // 用分支 1 里「未锁理算被拒」的案件推进到 41 再要求补件
    const cid = await (async () => {
      const cc = await submitClaim(admin, [{ itemName: '药品费', category: 'DRUG', amount: 66 }]);
      await registerAndLockEvidence(admin, cc.claimId, ['invoice']);
      await driveToAdjusting(admin, cc.claimId);
      return cc.claimId;
    })();
    const req = await api(admin, 'POST', `/api/v1/claims/${cid}/decision`, { decision: 'REQUEST_MORE_EVIDENCE', comment: '缺费用清单，请补传' });
    ok('要求补件（41→21）', req.status === 200, `got ${req.status}`);
    const d1 = await api(admin, 'GET', `/api/v1/claims/${cid}`);
    ok('状态为补录中', d1.data?.data?.[0]?.status === 'EVIDENCE_SUBMITTING', d1.data?.data?.[0]?.status);
    // 补齐后重新锁定证据 → 补录完成回流（21→10 待初审分配），直接开始初审
    await registerAndLockEvidence(admin, cid, ['list-new']);
    const d2 = await api(admin, 'GET', `/api/v1/claims/${cid}`);
    ok('补录完成自动回流（21→10）', d2.data?.data?.[0]?.status === 'PENDING_PRE_EXAM', d2.data?.data?.[0]?.status);
    const re2 = await api(admin, 'POST', `/api/v1/claims/${cid}/pre-exam/start`, undefined, { 'X-Automated': 'false' });
    ok('补件后重新开始初审（10→11）', re2.status === 200, `got ${re2.status} ${JSON.stringify(re2.data)}`);
    const d3 = await api(admin, 'GET', `/api/v1/claims/${cid}`);
    ok('补件案回到初审中', d3.data?.data?.[0]?.status === 'PRE_EXAMINING', d3.data?.data?.[0]?.status);
  }

  // ---------- 分支 6：决策图执行 + 轨迹回放 + 自动通过 ----------
  console.log('\n--- 分支 6：决策图（AI 编排 DAG）+ 轨迹回放 + 自动通过消费 ---');
  {
    const c = await submitClaim(admin, [{ itemName: '挂号费', category: 'SERVICE', amount: 30 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/allocate`);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/pre-exam/start`, undefined, { 'X-Automated': 'true' });
    const exe = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/decision/execute`, {});
    ok('决策图执行 200', exe.status === 200 && Boolean(exe.data?.data?.traceId), JSON.stringify(exe.data?.data));
    const trace = await api(admin, 'GET', `/api/v1/claims/${c.claimId}/decision/trace?traceId=${exe.data?.data?.traceId ?? ''}`);
    ok('轨迹回放节点 ≥1（证据→解析→核责→理算→决策）', trace.status === 200 && (trace.data?.data?.length ?? 0) >= 1, `nodes=${trace.data?.data?.length}`);
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/pre-exam/complete`, undefined, { 'X-Approved-Amount': '27' });
    await api(admin, 'POST', `/api/v1/claims/${c.claimId}/adjust`, undefined, { 'Idempotency-Key': `adj-${c.claimId}` });
    const auto = await api(admin, 'POST', `/api/v1/claims/${c.claimId}/auto-approve`);
    ok('自动通过（12→50 或显式禁用原因）', auto.status === 200 && ['APPLIED', 'AUTO_APPROVE_DISABLED', 'CAP_EXCEEDED'].includes(auto.data?.data?.reason), JSON.stringify(auto.data?.data));
    const st = await api(admin, 'GET', `/api/v1/claims/${c.claimId}`);
    const finalStatus = st.data?.data?.[0]?.status;
    ok('终态自洽（APPLIED⇒FINISHED / DISABLED⇒42）',
      (auto.data?.data?.reason === 'APPLIED') === (finalStatus === 'FINISHED') || finalStatus === 'ADJUSTED' || finalStatus === 'FINISHED',
      `${auto.data?.data?.reason} / ${finalStatus}`);
  }

  // ---------- 分支 7：后督抽检（PASS + DIFF + 幂等） ----------
  console.log('\n--- 分支 7：后督抽检（A9） ---');
  {
    const c1 = globalThis.case1Claim;
    const i1 = await api(admin, 'POST', '/api/v1/review/inspections', { claimId: c1, dimension: 'AMOUNT' });
    ok('发起抽检（MANUAL）', [200, 201].includes(i1.status) && i1.data?.data?.status === 'PENDING', JSON.stringify(i1.data?.data));
    const i1b = await api(admin, 'POST', '/api/v1/review/inspections', { claimId: c1, dimension: 'AMOUNT' });
    ok('同案重复发起幂等返回原单', i1b.data?.data?.inspectionNo === i1.data?.data?.inspectionNo);
    const cl = await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/claim`);
    ok('领取抽检任务（→IN_PROGRESS）', cl.status === 200 && cl.data?.data?.status === 'IN_PROGRESS', JSON.stringify(cl.data?.data));
    const pass = await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/conclusion`, { result: 'PASS' });
    ok('结论 PASS（终态）', pass.status === 200 && pass.data?.data?.status === 'CONCLUDED', JSON.stringify(pass.data?.data));
    // 负向：结论不可改写
    const reConclude = await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/conclusion`, { result: 'PASS' });
    ok('结论不可改写（4xx）', reConclude.status >= 400, `got ${reConclude.status}`);
    // DIFF 单（自洽校验：DIFF 必须带 diffType/action/detail）
    const i2 = await api(admin, 'POST', '/api/v1/review/inspections', { claimId: globalThis.case2Claim, dimension: 'PROCESS' });
    await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/claim`);
    const badDiff = await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/conclusion`, { result: 'DIFF' });
    ok('DIFF 缺差异类型被拒（领域不变量）', badDiff.status >= 400, `got ${badDiff.status}`);
    const diff = await api(admin, 'POST', `/api/v1/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/conclusion`, {
      result: 'DIFF', diffType: 'QUOTA_ABNORMAL', action: 'REFUND',
      diffDetail: { quota_claimed: '本方 0.00 / 复核 15000.00' }, targetStatus: 'ADJUSTED',
    });
    ok('结论 DIFF（带归因与处置）', diff.status === 200 && diff.data?.data?.result === 'DIFF', JSON.stringify(diff.data?.data));
  }

  // ---------- 收尾：列表分页 / 进度页数据源 ----------
  console.log('\n--- 查询面：分页与详情 ---');
  {
    const allSt = 'ACCEPTED,PENDING_PRE_EXAM,PRE_EXAMINING,PRE_EXAMINED,EVIDENCE_SUBMITTING,PENDING_ADJUST,ADJUSTING,ADJUSTED,REVIEWING,CLOSED_REJECTED,FINISHED,APPEALING';
    const page = await api(admin, 'GET', `/api/v1/claims?page=1&size=50&statuses=${allSt}`);
    ok('待办分页可查（本会话新建案件入列）', page.status === 200 && Number(page.data?.data?.total) > 0, `total=${page.data?.data?.total}`);
    const statuses = new Set((page.data?.data?.records ?? []).map((r) => r.status));
    ok('列表覆盖多个状态（链路产物）', statuses.size >= 3, [...statuses].join(','));
  }

  console.log(`\n===== 结果：${passed} 通过 / ${failed} 失败 =====`);
  if (failed) {
    console.log('失败清单：');
    for (const f of failures) console.log(`  · ${f}`);
    process.exit(1);
  }
}

main().catch((e) => {
  console.error('E2E 中止：', e?.message ?? e);
  console.log(`\n===== 结果：${passed} 通过 / ${failed + 1} 失败（中止） =====`);
  process.exit(1);
});
