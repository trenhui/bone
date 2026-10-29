#!/usr/bin/env node
/**
 * 智能理赔全功能 E2E（synap-claim，A1-A9 全能力）。
 *
 * 设计原则
 * --------------------------------
 * · **只走真实 API**（经网关，与前端同口径），不直插 DB——测试数据 = 业务代码的真实产物；
 *   收尾仅用 MySQL 直查做**第二证据源对账**（防「接口 200 但没落库」的假绿）；
 *   唯一写例外 = **种子夹具整备**（运行前回补 E2E 额度账户可用额度，见下 mysqlExec），
 *   与 cleanup-residue 同类的夹具维护、非造数：只动 tenant 0 种子行且历史三值不改；
 * · **公共层唯一真源**（README §1 分层铁律）：配置/登录/HTTP/断言取自 e2e_lib/client.cjs
 *   （BASE/USER/PWD、login/loginAs、api、makeChecker），造数取自 e2e_lib/fixtures.cjs；
 *   脚本内不再复制 login/api/check 样板，不写圆整金额与裸测试值；
 * · **多角色**：admin（第一审核人）+ claim_reviewer2（复核/复议岗）+ claim_auditor（结算首审），
 *   经 IAM 真实管理链路建账号绑权限，双人复核的职责分离（SoD）才可测；
 * · **多分支**：小额直付 / 大额双人复核 / 拒赔+申诉维持 / 拒赔+申诉推翻 / 补件 / 挂起恢复 /
 *   自动通过 / 抽检 PASS+DIFF；
 * · **负向探针**：失效保单、被保人不匹配、未锁证据理算、同人复核——「能走通」之外还要「该拒的拒」；
 * · **数据生产化**（README §3）：金额/明细拆分/文案/日期出自 fixtures 数据集（同 STAMP 同数据，
 *   Σ 明细恒等于申请金额）；保单/被保人/责任/额度为种子引用数据，真源
 *   scripts/migration/0073_seed_claim_reference_data.sql，口径固定不随 STAMP 变。
 *
 * 运行：./scripts/e2e/run.sh claim-full-chain-e2e.mjs
 *      （或 node scripts/e2e/claim-full-chain-e2e.mjs；直连后端排查见 README §5）
 * 退出码：0 = 全部断言通过；1 = 任意失败（失败清单见输出）。
 *         设 BONE_E2E_JUNIT_DIR=<dir> 时另按套件落 JUnit XML（CI 消费，见 README §4.7）。
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BASE, STAMP, login, loginAs, api, makeChecker } from './e2e_lib/client.cjs';
import { dataset } from './e2e_lib/fixtures.cjs';

const { check, report } = makeChecker();

const REVIEWER2_USER = 'claim_reviewer2';
const REVIEWER2_PASS = 'Claim2@2026';
const REVIEWER2_ROLE_CODE = 'CLAIM_REVIEWER';
/** 种子引用数据（保单/被保人/责任/额度），真源 scripts/migration/0073_seed_claim_reference_data.sql */
const POLICY = { policyNo: 'E2E-POLICY-0001', planId: '9001', insuredIdHash: 'e2e-insured-hash-0001' };

// ---------- 生产化数据（README §3：每个值只生成一次存变量，创建与断言共用） ----------
const ds = dataset(STAMP);
const OUT_INSURE = `${ds.days_ago(9)}T09:00:00Z`; // 出险时间：运行日倒推的自然业务日期
const NEG_AMT = ds.amount(10, 80); // 报案前置负向探针（失效保单/被保人不匹配）
const UNLOCKED_AMT = ds.amount(40, 180); // 未锁证据理算负向案
const TOTAL1 = ds.amount(150, 760); // 分支 1 小额直付（<1000，Σ 明细恒等于它）
const ITEM1 = ds.split(TOTAL1, 3); // 守恒拆分：Σ == TOTAL1
const TOTAL2 = ds.amount(12000, 48000); // 分支 2 大额双人复核（≥1 万）
const ITEM2 = ds.split(TOTAL2, 2);
const TOTAL3 = ds.amount(80, 420); // 分支 3 拒赔 + 申诉维持
const TOTAL4 = ds.amount(180, 860); // 分支 4 申诉推翻 + 挂起/恢复
const TOTAL5 = ds.amount(50, 260); // 分支 5 补件循环
const TOTAL6 = ds.amount(25, 110); // 分支 6 决策图 + 自动通过
const APPROVED6 = Math.round(TOTAL6 * 0.9 * 100) / 100; // 初审按 90% 责任核定（与责任 pay_percent 同口径）
const CMT1 = `${ds.city}客户门诊费用直付，材料齐全`;
const CMT2 = `${ds.city}客户手术费用大额复核通过`;

const sha256 = async (s) =>
  Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

/** 登记并锁定证据集（理算前置）。 */
async function registerAndLockEvidence(token, claimId, names) {
  for (const n of names) {
    const contentHash = await sha256(`${claimId}:${n}:${Date.now()}`);
    const r = await api('POST', `/claims/${claimId}/evidences`, {
      fileRef: `claim/${claimId}/${n}.pdf`,
      contentHash,
      docType: n.includes('list') ? 'LIST' : 'INVOICE',
      verifyValid: 'PASS',
    }, { token, headers: { 'Idempotency-Key': `ev-${claimId}-${n}-${Math.random().toString(16).slice(2, 8)}` } });
    if (r.status !== 201 && r.status !== 200) throw new Error(`登记材料失败: ${JSON.stringify(r.data)}`);
  }
  const lock = await api('POST', `/claims/${claimId}/evidences/lock`, undefined, { token });
  if (lock.status !== 200) throw new Error(`锁定证据失败: ${JSON.stringify(lock.data)}`);
  return lock;
}

/** 主干推进：受理 → 分配 → 初审 → 转人工核赔 → 领取。返回时案件处于 41（核赔中）。 */
async function driveToAdjusting(token, claimId, approvedAmount = 0) {
  const steps = [
    ['allocate', () => api('POST', `/claims/${claimId}/allocate`, undefined, { token })],
    ['pre-exam/start', () => api('POST', `/claims/${claimId}/pre-exam/start`, undefined, { token, headers: { 'X-Automated': 'false' } })],
    // 核定金额 = 初审结论，理算基准（明细 valid_amount）由后端按此回填；传 0 等于核定「零赔付」
    ['pre-exam/complete', () => api('POST', `/claims/${claimId}/pre-exam/complete`, undefined, { token, headers: { 'X-Approved-Amount': String(approvedAmount) } })],
    ['escalate', () => api('POST', `/claims/${claimId}/escalate`, undefined, { token })],
    ['adjust/claim', () => api('POST', `/claims/${claimId}/adjust/claim`, undefined, { token })],
  ];
  for (const [name, fn] of steps) {
    const r = await fn();
    if (r.status !== 200) throw new Error(`${name} 失败: ${JSON.stringify(r.data)}`);
  }
}

async function submitClaim(token, items, outInsureTime = OUT_INSURE, policy = POLICY) {
  const r = await api('POST', '/claims', { ...policy, claimType: 'OUTPATIENT', outInsureTime, items },
    { token, headers: { 'Idempotency-Key': `e2e-${Date.now()}-${Math.random().toString(16).slice(2, 8)}` } });
  if (r.status !== 201) throw new Error(`报案失败: ${JSON.stringify(r.data)}`);
  return r.data.data;
}

/** 账号/角色幂等保障：按 code/用户名查，缺则建（真实 IAM 管理链路），存在则重置密码。 */
async function ensureClaimUser(username, realName) {
  // 1) 角色：按 code 查，缺则建
  let roleId = null;
  const roles = await api('GET', '/iam/roles?page=1&size=100');
  const found = (roles.data?.data?.records ?? []).find((x) => x.code === REVIEWER2_ROLE_CODE);
  if (found) {
    roleId = found.id;
  } else {
    const created = await api('POST', '/iam/roles', {
      name: '理赔复核岗', code: REVIEWER2_ROLE_CODE, description: '智能理赔双人复核/复议岗（E2E）', tenantId: 0,
    });
    roleId = created.data?.data;
    check('创建复核岗角色', roleId != null && roleId !== '', JSON.stringify(created.data));
  }
  // 2) 权限：claim:* 六码
  const perms = await api('GET', '/iam/permissions?page=1&size=500');
  const claimPerms = (perms.data?.data?.records ?? []).filter((p) => p.code?.startsWith('claim:'));
  const assign = await api('POST', `/iam/roles/${roleId}/permissions`,
    { permissionIds: claimPerms.map((p) => p.id) });
  check(`绑定 claim 权限到复核岗（${username}）`, assign.status === 200, JSON.stringify(assign.data));
  // 3) 账号：按用户名查，缺则建（带角色+部门）
  const accounts = await api('GET', `/iam/accounts?page=1&size=200&keyword=${username}`);
  const acc = (accounts.data?.data?.records ?? []).find((a) => a.username === username);
  if (!acc) {
    // 归属部门必填：取部门树第一个节点（真实管理链路同款动作）
    const depts = await api('GET', '/iam/depts/tree');
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
    const created = await api('POST', '/iam/accounts', {
      username, password: REVIEWER2_PASS, email: `${username}@e2e.bone.local`,
      realName, roleIds: [roleId], deptId,
    });
    check(`创建账号 ${username}`, created.status === 200, JSON.stringify(created.data));
  } else {
    await api('POST', `/iam/accounts/${acc.id}/reset-password`, { newPassword: REVIEWER2_PASS });
  }
  return loginAs(username, REVIEWER2_PASS, BASE);
}

// ---------- 第二证据源：MySQL 落库对账 ----------
// 连接配置与口令的唯一真源 = e2e_lib/config.py + e2e_lib/mysql.py；Node 侧不复制凭据，
// 经 python3 子进程复用同一 query()（README §5：口令只从环境变量取）。
const HERE = path.dirname(fileURLToPath(import.meta.url));
const MYSQL_PY =
  'import sys,json;sys.path.insert(0,sys.argv[2]);from e2e_lib.mysql import query;' +
  'o,e=query(sys.argv[1]);print(json.dumps([o,e],ensure_ascii=False))';

/** 执行只读 SQL 并解析首行（mysql -e 的 TSV：首行表头、次行数据）。失败返回 {err}，不断栈。 */
function mysqlRow(sql) {
  const py = spawnSync(process.env.PYTHON || 'python3', ['-c', MYSQL_PY, sql, HERE],
    { encoding: 'utf8', timeout: 35000 });
  if (py.error || py.status !== 0) {
    return { err: String(py.error?.message || py.stderr || `rc=${py.status}`).trim().slice(0, 200) };
  }
  let out = null;
  let err = null;
  try {
    [out, err] = JSON.parse(String(py.stdout).trim().split('\n').pop());
  } catch {
    return { err: `结果解析失败: ${String(py.stdout).slice(0, 160)}` };
  }
  if (err) return { err: String(err) };
  const lines = String(out ?? '').split('\n').filter((l) => l.trim() !== '');
  if (lines.length < 2) return { err: '无数据行' };
  const cols = lines[0].split('\t');
  const vals = lines[1].split('\t');
  const row = {};
  cols.forEach((c, i) => { row[c] = vals[i]; });
  return { row };
}

/** E2E 额度账户的种子水位（0073 seed quota_initial=100000）：每轮运行前可用额度回到该水位。 */
const QUOTA_FLOOR = 100000;

/**
 * 执行 SQL 并返回 {out} 或 {err}（与 mysqlRow 同通道，走 e2e_lib/mysql.query 唯一凭据真源）。
 *
 * **仅限种子夹具整备这一个写路径**（下方 quotaTopUp）：额度账户是所有轮次共享的有限资源，
 * 演示案件按约定保留（claimed 不回收）、中断残留的 frozen 也长期占用，多轮后可用额度耗尽，
 * 结算建单会以 SC_CLAIM_QUOTA_INSUFFICIENT 失败（曾致复跑分支 2 三连红）。
 * 业务单据一律走 API，不从本函数写。
 */
function mysqlExec(sql) {
  const py = spawnSync(process.env.PYTHON || 'python3', ['-c', MYSQL_PY, sql, HERE],
    { encoding: 'utf8', timeout: 35000 });
  if (py.error || py.status !== 0) {
    return { err: String(py.error?.message || py.stderr || `rc=${py.status}`).trim().slice(0, 200) };
  }
  try {
    const [, err] = JSON.parse(String(py.stdout).trim().split('\n').pop());
    return err ? { err: String(err) } : { out: true };
  } catch {
    return { err: `结果解析失败: ${String(py.stdout).slice(0, 160)}` };
  }
}

async function main() {
  console.log(`\n===== 智能理赔全功能 E2E（${BASE} · 数据集 ${STAMP}） =====\n`);
  const admin = await login(BASE);
  check('admin 登录（tenant 0）', true);
  const reviewer2 = await ensureClaimUser(REVIEWER2_USER, ds.manager);
  check('复核岗账号可用（reviewer2 登录）', Boolean(reviewer2));
  // 大额资金链需第三个身份：结算首审 ≠ 案件第一审核人（admin），结算复核 ≠ 首审发起人（auditor）
  const auditor = await ensureClaimUser('claim_auditor', ds.person());
  check('结算审核账号可用（claim_auditor 登录）', Boolean(auditor));

  // ---------- 种子夹具整备：E2E 额度账户可用额度回到种子水位 ----------
  // 只增不减（GREATEST）：claimed/frozen 历史与守恒不变量 initial−claimed−frozen≥0 都不动；
  // 定点 tenant 0 + 种子保单/被保人/当年 PERSONAL，与 0073 findAccount 同口径，幂等。
  const topUp = mysqlExec(
    `UPDATE t_quota_account` +
    ` SET quota_initial = GREATEST(quota_initial, quota_claimed + quota_frozen + ${QUOTA_FLOOR}.00),` +
    `     updated_at = CURRENT_TIMESTAMP(3)` +
    ` WHERE insured_id_hash = '${POLICY.insuredIdHash}' AND policy_no = '${POLICY.policyNo}'` +
    `   AND quota_year = YEAR(CURDATE()) AND quota_type = 'PERSONAL'` +
    `   AND tenant_id = 0 AND deleted = 0` +
    `   AND quota_initial < quota_claimed + quota_frozen + ${QUOTA_FLOOR}.00`);
  const avail = mysqlRow(
    `SELECT ROUND(quota_initial - quota_claimed - quota_frozen, 2) AS available FROM t_quota_account` +
    ` WHERE insured_id_hash = '${POLICY.insuredIdHash}' AND policy_no = '${POLICY.policyNo}'` +
    `   AND quota_year = YEAR(CURDATE()) AND quota_type = 'PERSONAL' AND tenant_id = 0 AND deleted = 0`);
  check(`运行前额度夹具整备（可用额度回到种子水位 ≥${QUOTA_FLOOR}，历史三值不改）`,
    !topUp.err && !avail.err && Number(avail.row?.available) >= QUOTA_FLOOR,
    topUp.err || avail.err || `available=${avail.row?.available}`);

  // ---------- 负向：失效保单 / 被保人不匹配 ----------
  console.log('\n--- 负向：报案前置校验 ---');
  {
    const r1 = await api('POST', '/claims',
      { ...POLICY, policyNo: 'E2E-POLICY-EXPIRED', planId: '9002', claimType: 'OUTPATIENT', outInsureTime: OUT_INSURE, items: [{ itemName: '门诊诊查费', category: 'SERVICE', quantity: 1, unitPrice: NEG_AMT, amount: NEG_AMT }] },
      { token: admin, headers: { 'Idempotency-Key': `neg-${Date.now()}` } });
    check('失效保单被拒（422 SC_CLAIM_POLICY_NOT_ACTIVE）', r1.status === 422, `got ${r1.status}`);
    const r2 = await api('POST', '/claims',
      { ...POLICY, insuredIdHash: 'wrong-insured', claimType: 'OUTPATIENT', outInsureTime: OUT_INSURE, items: [{ itemName: '门诊诊查费', category: 'SERVICE', quantity: 1, unitPrice: NEG_AMT, amount: NEG_AMT }] },
      { token: admin, headers: { 'Idempotency-Key': `neg2-${Date.now()}` } });
    check('被保人不匹配被拒（403）', r2.status === 403, `got ${r2.status}`);
  }

  // ---------- 分支 1：小额全链（报案→材料→理算→核赔→资金→支付→结案→额度） ----------
  console.log('\n--- 分支 1：小额直付全链（生产化明细，Σ = 申请金额） ---');
  {
    const c = await submitClaim(admin, [
      { itemName: '门诊诊查费', category: 'SERVICE', quantity: 1, unitPrice: ITEM1[0], amount: ITEM1[0] },
      { itemName: '检查费', category: 'EXAM', quantity: 1, unitPrice: ITEM1[1], amount: ITEM1[1] },
      { itemName: '药品费', category: 'DRUG', quantity: 1, unitPrice: ITEM1[2], amount: ITEM1[2] },
    ]);
    check('受理 201 / 对外 PENDING_EVIDENCE', c.status === 'PENDING_EVIDENCE', c.status);
    await registerAndLockEvidence(admin, c.claimId, ['invoice', 'list']);

    // 负向探针已在锁前被绕过：这里用另一案验证「未锁不能理算」
    const c2 = await submitClaim(admin, [{ itemName: '检验费', category: 'EXAM', quantity: 1, unitPrice: UNLOCKED_AMT, amount: UNLOCKED_AMT }]);
    await api('POST', `/claims/${c2.claimId}/evidences`, { fileRef: `claim/${c2.claimId}/a.pdf`, contentHash: await sha256(`${c2.claimId}:a`), verifyValid: 'PASS' }, { token: admin, headers: { 'Idempotency-Key': `neg3-${c2.claimId}` } });
    const neg = await api('POST', `/claims/${c2.claimId}/adjust`, undefined, { token: admin });
    check('证据未锁定理算被拒（404/422）', [404, 422].includes(neg.status), `got ${neg.status}`);
    // 该案继续作为分支 5（补件）素材：推进到 41 再补件
    await registerAndLockEvidence(admin, c2.claimId, ['invoice-fixed']);
    await driveToAdjusting(admin, c2.claimId, UNLOCKED_AMT);

    await driveToAdjusting(admin, c.claimId, TOTAL1);
    const adj = await api('POST', `/claims/${c.claimId}/adjust`, undefined, { token: admin, headers: { 'Idempotency-Key': `adj-${c.claimId}` } });
    check('执行理算 200（90% 责任）', adj.status === 200 && adj.data?.data?.totalAmount > 0, JSON.stringify(adj.data?.data?.totalAmount));
    check('公式哈希与快照非空', Boolean(adj.data?.data?.formulaHash && adj.data?.data?.snapshot));
    const conf = await api('POST', `/claims/${c.claimId}/adjust/confirm`, undefined, { token: admin });
    check('理算确认 200', conf.status === 200, `got ${conf.status}`);

    const dec = await api('POST', `/claims/${c.claimId}/decision`, { decision: 'APPROVED', approvedAmount: adj.data.data.totalAmount, comment: CMT1 }, { token: admin });
    check('人工核赔通过（41→42）', dec.status === 200, `got ${dec.status}`);

    const st = await api('POST', `/claims/${c.claimId}/settlement`, { attempt: 1, accountType: 'PERSONAL' }, { token: admin, headers: { 'Idempotency-Key': `st-${c.claimId}` } });
    check('建结算单（软占额度）', [200, 201].includes(st.status) && Boolean(st.data?.data?.settlementNo), JSON.stringify(st.data?.data));
    const ap = await api('POST', `/claims/${c.claimId}/settlement/audit-pass`, undefined, { token: admin });
    check('结算审核（小额直接硬预占）', ap.status === 200 && ap.data?.data?.fundingStage === 'HARD_RESERVED', JSON.stringify(ap.data?.data?.fundingStage));
    const pay = await api('POST', `/claims/${c.claimId}/settlement/pay`, undefined, { token: admin });
    check('支付完成', pay.status === 200, `got ${pay.status}`);
    const detail = await api('GET', `/claims/${c.claimId}`, undefined, { token: admin });
    check('结案 FINISHED（支付完成自动结案 42→50）', detail.data?.data?.[0]?.status === 'FINISHED', JSON.stringify({ status: detail.data?.data?.[0]?.status, ext: detail.data?.data?.[0]?.externalStatus }));
    const quota = await api('GET', `/claims/${c.claimId}/quota`, undefined, { token: admin });
    check('额度账户三值可见（claimed 增加）', quota.status === 200 && quota.data?.data?.claimed > 0, JSON.stringify(quota.data?.data));
    globalThis.case1Claim = c.claimId;
  }

  // ---------- 分支 2：大额双人复核 + SoD ----------
  console.log('\n--- 分支 2：大额双人复核（≥1 万）+ 职责分离 ---');
  {
    const c = await submitClaim(admin, [
      { itemName: '手术费', category: 'SERVICE', quantity: 1, unitPrice: ITEM2[0], amount: ITEM2[0] },
      { itemName: '医用材料费', category: 'MATERIAL', quantity: 1, unitPrice: ITEM2[1], amount: ITEM2[1] },
    ]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice', 'medical-record']);
    await driveToAdjusting(admin, c.claimId, TOTAL2);
    const adj = await api('POST', `/claims/${c.claimId}/adjust`, undefined, { token: admin, headers: { 'Idempotency-Key': `adj-${c.claimId}` } });
    check('大额理算 200', adj.status === 200, `got ${adj.status} ${JSON.stringify(adj.data?.detail ?? adj.data?.message ?? '')}`);
    await api('POST', `/claims/${c.claimId}/adjust/confirm`, undefined, { token: admin });
    await api('POST', `/claims/${c.claimId}/decision`, { decision: 'APPROVED', approvedAmount: adj.data?.data?.totalAmount ?? TOTAL2, comment: CMT2 }, { token: admin });
    // 结算两段式：建单（软占）→ 第一道审核（≥1 万进入复核冻结；审核人须 ≠ 案件第一审核人）
    const st2 = await api('POST', `/claims/${c.claimId}/settlement`, { attempt: 1, accountType: 'PERSONAL' }, { token: admin, headers: { 'Idempotency-Key': `st-${c.claimId}` } });
    check('大额建结算单（软占额度）', [200, 201].includes(st2.status) && Boolean(st2.data?.data?.settlementNo),
      JSON.stringify(st2.data));
    const audit = await api('POST', `/claims/${c.claimId}/settlement/audit-pass`, undefined, { token: auditor });
    check('大额结算审核进入复核冻结', audit.status === 200 && audit.data?.data?.fundingStage === 'REVIEW_FREEZE',
      `got ${audit.status} ${JSON.stringify(audit.data?.data ?? audit.data)}`);
    // 负向：第一审核人不能发起/执行双人复核（发起即校验复核人 ≠ 第一审核人）
    const samePerson = await api('POST', `/claims/${c.claimId}/second-review`, undefined, { token: admin });
    check('第一审核人发起复核被拒（SoD）', samePerson.status >= 400, `got ${samePerson.status}`);
    // 负向：结算复核也不能由第一审核人放行
    const settleSame = await api('POST', `/claims/${c.claimId}/settlement/review-confirm`, undefined, { token: admin });
    check('结算复核同人被拒（SoD）', [403, 409].includes(settleSame.status) || settleSame.status >= 400, `got ${settleSame.status}`);
    // 复核岗：发起（42→44）→ 结论（44→50）→ 结算复核放行 → 支付
    const sr = await api('POST', `/claims/${c.claimId}/second-review`, undefined, { token: reviewer2 });
    check('复核岗发起双人复核（42→44）', sr.status === 200, `got ${sr.status} ${JSON.stringify(sr.data)}`);
    const done = await api('POST', `/claims/${c.claimId}/second-review/complete`, undefined, { token: reviewer2, headers: { 'X-Approved': 'true' } });
    check('复核人放行（44→50）', done.status === 200, `got ${done.status} ${JSON.stringify(done.data)}`);
    const rc = await api('POST', `/claims/${c.claimId}/settlement/review-confirm`, undefined, { token: reviewer2 });
    check('结算大额复核放行（REVIEW_FREEZE→HARD_RESERVED）', rc.status === 200 && rc.data?.data?.fundingStage === 'HARD_RESERVED',
      `got ${rc.status} ${JSON.stringify(rc.data?.data ?? rc.data)}`);
    const pay = await api('POST', `/claims/${c.claimId}/settlement/pay`, undefined, { token: admin });
    check('大额支付完成', pay.status === 200, `got ${pay.status} ${JSON.stringify(pay.data?.message ?? '')}`);
    const detail = await api('GET', `/claims/${c.claimId}`, undefined, { token: admin });
    check('大额案复核后结案', detail.data?.data?.[0]?.status === 'FINISHED', detail.data?.data?.[0]?.status);
    globalThis.case2Claim = c.claimId;
  }

  // ---------- 分支 3：拒赔 + 申诉维持 ----------
  console.log('\n--- 分支 3：拒赔 + 申诉维持（UPHELD） ---');
  {
    const c = await submitClaim(admin, [{ itemName: '理疗费', category: 'SERVICE', quantity: 1, unitPrice: TOTAL3, amount: TOTAL3 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await driveToAdjusting(admin, c.claimId, TOTAL3);
    const dec = await api('POST', `/claims/${c.claimId}/decision`, {
      decision: 'REJECTED', rejectType: '01', comment: JSON.stringify({ factFindings: '既往症就诊', clauseBasis: '条款第 7.2 条责任免除', evidenceRefs: [`claim/${c.claimId}/invoice.pdf`], appealChannel: '客服热线 95xxx', legalDeadline: '收到拒赔通知后 48 小时内' }),
    }, { token: admin });
    check('拒赔生效（41→45）', dec.status === 200, `got ${dec.status}`);
    const ap = await api('POST', `/claims/${c.claimId}/appeal`, { reason: '对拒赔结论有异议：本次为初次确诊' }, { token: admin });
    check('发起申诉（45→60，201）', [200, 201].includes(ap.status) && ap.data?.data?.appealNo, `got ${ap.status} ${JSON.stringify(ap.data?.data)}`);
    const up = await api('POST', `/claims/${c.claimId}/appeal/review`, { result: 'UPHELD', reason: '复议维持：既往症属责任免除' }, { token: reviewer2 });
    check('复议维持（UPHELD）', up.status === 200 && up.data?.data?.reviewResult === 'UPHELD', JSON.stringify(up.data?.data));
    globalThis.case3Claim = c.claimId;
  }

  // ---------- 分支 4：拒赔 + 申诉推翻（REOPEN）+ 挂起/恢复 ----------
  console.log('\n--- 分支 4：申诉推翻重启审核 + 挂起/恢复 ---');
  {
    const c = await submitClaim(admin, [{ itemName: 'CT 检查费', category: 'EXAM', quantity: 1, unitPrice: TOTAL4, amount: TOTAL4 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await driveToAdjusting(admin, c.claimId, TOTAL4);
    await api('POST', `/claims/${c.claimId}/decision`, {
      decision: 'REJECTED', rejectType: '02', comment: JSON.stringify({ factFindings: '材料存疑', clauseBasis: '条款 7.3', evidenceRefs: [], appealChannel: '95xxx', legalDeadline: '48h' }),
    }, { token: admin });
    const ap = await api('POST', `/claims/${c.claimId}/appeal`, { reason: '已补充病历材料，申请复议' }, { token: admin });
    const re = await api('POST', `/claims/${c.claimId}/appeal/review`, { result: 'REOPEN', reason: '新证据成立，推翻原结论' }, { token: reviewer2 });
    check('复议推翻重启审核（REOPEN）', re.status === 200 && re.data?.data?.reviewResult === 'REOPEN', JSON.stringify(re.data?.data));
    const detail = await api('GET', `/claims/${c.claimId}`, undefined, { token: admin });
    const stAfterReopen = detail.data?.data?.[0]?.status;
    check('推翻后回到审核段（非终态）', ['PENDING_ADJUST', 'ADJUSTING', 'ADJUSTED'].includes(stAfterReopen), stAfterReopen);
    // 挂起 / 恢复（正交机制）
    const hang = await api('POST', `/claims/${c.claimId}/hangup`, undefined, { token: admin, headers: { 'X-Hangup-Action': 'HANG_UP', 'X-Hangup-Type': 'EVIDENCE' } });
    check('挂起（EVIDENCE）', hang.status === 200, `got ${hang.status}`);
    const hung = await api('GET', `/claims/${c.claimId}`, undefined, { token: admin });
    check('挂起态可见（hang_up_status=1）', hung.data?.data?.[0]?.hangUpStatus === 1, String(hung.data?.data?.[0]?.hangUpStatus));
    const resume = await api('POST', `/claims/${c.claimId}/hangup`, undefined, { token: admin, headers: { 'X-Hangup-Action': 'RESUME' } });
    check('恢复', resume.status === 200, `got ${resume.status}`);
    globalThis.case4Claim = c.claimId;
  }

  // ---------- 分支 5：补件（REQUEST_MORE_EVIDENCE → 21 → 补齐再走） ----------
  console.log('\n--- 分支 5：补件循环 ---');
  {
    const cid = await (async () => {
      const cc = await submitClaim(admin, [{ itemName: '药品费', category: 'DRUG', quantity: 1, unitPrice: TOTAL5, amount: TOTAL5 }]);
      await registerAndLockEvidence(admin, cc.claimId, ['invoice']);
      await driveToAdjusting(admin, cc.claimId);
      return cc.claimId;
    })();
    const req = await api('POST', `/claims/${cid}/decision`, { decision: 'REQUEST_MORE_EVIDENCE', comment: '缺费用清单，请补传' }, { token: admin });
    check('要求补件（41→21）', req.status === 200, `got ${req.status}`);
    const d1 = await api('GET', `/claims/${cid}`, undefined, { token: admin });
    check('状态为补录中', d1.data?.data?.[0]?.status === 'EVIDENCE_SUBMITTING', d1.data?.data?.[0]?.status);
    // 补齐后重新锁定证据 → 补录完成回流（21→10 待初审分配），直接开始初审
    await registerAndLockEvidence(admin, cid, ['list-new']);
    const d2 = await api('GET', `/claims/${cid}`, undefined, { token: admin });
    check('补录完成自动回流（21→10）', d2.data?.data?.[0]?.status === 'PENDING_PRE_EXAM', d2.data?.data?.[0]?.status);
    const re2 = await api('POST', `/claims/${cid}/pre-exam/start`, undefined, { token: admin, headers: { 'X-Automated': 'false' } });
    check('补件后重新开始初审（10→11）', re2.status === 200, `got ${re2.status} ${JSON.stringify(re2.data)}`);
    const d3 = await api('GET', `/claims/${cid}`, undefined, { token: admin });
    check('补件案回到初审中', d3.data?.data?.[0]?.status === 'PRE_EXAMINING', d3.data?.data?.[0]?.status);
  }

  // ---------- 分支 6：决策图执行 + 轨迹回放 + 自动通过 ----------
  console.log('\n--- 分支 6：决策图（AI 编排 DAG）+ 轨迹回放 + 自动通过消费 ---');
  {
    const c = await submitClaim(admin, [{ itemName: '挂号费', category: 'SERVICE', quantity: 1, unitPrice: TOTAL6, amount: TOTAL6 }]);
    await registerAndLockEvidence(admin, c.claimId, ['invoice']);
    await api('POST', `/claims/${c.claimId}/allocate`, undefined, { token: admin });
    await api('POST', `/claims/${c.claimId}/pre-exam/start`, undefined, { token: admin, headers: { 'X-Automated': 'true' } });
    const exe = await api('POST', `/claims/${c.claimId}/decision/execute`, {}, { token: admin });
    check('决策图执行 200', exe.status === 200 && Boolean(exe.data?.data?.traceId), JSON.stringify(exe.data?.data));
    const trace = await api('GET', `/claims/${c.claimId}/decision/trace?traceId=${exe.data?.data?.traceId ?? ''}`, undefined, { token: admin });
    check('轨迹回放节点 ≥1（证据→解析→核责→理算→决策）', trace.status === 200 && (trace.data?.data?.length ?? 0) >= 1, `nodes=${trace.data?.data?.length}`);
    await api('POST', `/claims/${c.claimId}/pre-exam/complete`, undefined, { token: admin, headers: { 'X-Approved-Amount': String(APPROVED6) } });
    await api('POST', `/claims/${c.claimId}/adjust`, undefined, { token: admin, headers: { 'Idempotency-Key': `adj-${c.claimId}` } });
    const auto = await api('POST', `/claims/${c.claimId}/auto-approve`, undefined, { token: admin });
    check('自动通过（12→50 或显式禁用原因）', auto.status === 200 && ['APPLIED', 'AUTO_APPROVE_DISABLED', 'CAP_EXCEEDED'].includes(auto.data?.data?.reason), JSON.stringify(auto.data?.data));
    const st = await api('GET', `/claims/${c.claimId}`, undefined, { token: admin });
    const finalStatus = st.data?.data?.[0]?.status;
    check('终态自洽（APPLIED⇒FINISHED / DISABLED⇒42）',
      (auto.data?.data?.reason === 'APPLIED') === (finalStatus === 'FINISHED') || finalStatus === 'ADJUSTED' || finalStatus === 'FINISHED',
      `${auto.data?.data?.reason} / ${finalStatus}`);
  }

  // ---------- 分支 7：后督抽检（PASS + DIFF + 幂等） ----------
  console.log('\n--- 分支 7：后督抽检（A9） ---');
  {
    const c1 = globalThis.case1Claim;
    const i1 = await api('POST', '/review/inspections', { claimId: c1, dimension: 'AMOUNT' }, { token: admin });
    check('发起抽检（MANUAL）', [200, 201].includes(i1.status) && i1.data?.data?.status === 'PENDING', JSON.stringify(i1.data?.data));
    const i1b = await api('POST', '/review/inspections', { claimId: c1, dimension: 'AMOUNT' }, { token: admin });
    check('同案重复发起幂等返回原单', i1b.data?.data?.inspectionNo === i1.data?.data?.inspectionNo);
    const cl = await api('POST', `/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/claim`, undefined, { token: admin });
    check('领取抽检任务（→IN_PROGRESS）', cl.status === 200 && cl.data?.data?.status === 'IN_PROGRESS', JSON.stringify(cl.data?.data));
    const pass = await api('POST', `/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/conclusion`, { result: 'PASS' }, { token: admin });
    check('结论 PASS（终态）', pass.status === 200 && pass.data?.data?.status === 'CONCLUDED', JSON.stringify(pass.data?.data));
    // 负向：结论不可改写
    const reConclude = await api('POST', `/review/inspections/${encodeURIComponent(i1.data.data.inspectionNo)}/conclusion`, { result: 'PASS' }, { token: admin });
    check('结论不可改写（4xx）', reConclude.status >= 400, `got ${reConclude.status}`);
    // DIFF 单（自洽校验：DIFF 必须带 diffType/action/detail）
    const i2 = await api('POST', '/review/inspections', { claimId: globalThis.case2Claim, dimension: 'PROCESS' }, { token: admin });
    await api('POST', `/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/claim`, undefined, { token: admin });
    const badDiff = await api('POST', `/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/conclusion`, { result: 'DIFF' }, { token: admin });
    check('DIFF 缺差异类型被拒（领域不变量）', badDiff.status >= 400, `got ${badDiff.status}`);
    const diff = await api('POST', `/review/inspections/${encodeURIComponent(i2.data.data.inspectionNo)}/conclusion`, {
      result: 'DIFF', diffType: 'QUOTA_ABNORMAL', action: 'REFUND',
      diffDetail: { quota_claimed: `本方 0.00 / 复核 ${TOTAL2.toFixed(2)}` }, targetStatus: 'ADJUSTED',
    }, { token: admin });
    check('结论 DIFF（带归因与处置）', diff.status === 200 && diff.data?.data?.result === 'DIFF', JSON.stringify(diff.data?.data));
  }

  // ---------- 查询面：列表分页 / 进度页数据源 ----------
  console.log('\n--- 查询面：分页与详情 ---');
  {
    const allSt = 'ACCEPTED,PENDING_PRE_EXAM,PRE_EXAMINING,PRE_EXAMINED,EVIDENCE_SUBMITTING,PENDING_ADJUST,ADJUSTING,ADJUSTED,REVIEWING,CLOSED_REJECTED,FINISHED,APPEALING';
    const page = await api('GET', `/claims?page=1&size=50&statuses=${allSt}`, undefined, { token: admin });
    check('待办分页可查（本会话新建案件入列）', page.status === 200 && Number(page.data?.data?.total) > 0, `total=${page.data?.data?.total}`);
    const statuses = new Set((page.data?.data?.records ?? []).map((r) => r.status));
    check('列表覆盖多个状态（链路产物）', statuses.size >= 3, [...statuses].join(','));
  }

  // ---------- 第二证据源：MySQL 落库对账（防「接口 200 但没落库」） ----------
  console.log('\n--- 第二证据源：MySQL 落库对账 ---');
  {
    const r1 = mysqlRow(`SELECT status, claim_amount FROM t_claim_case WHERE id = ${globalThis.case1Claim} AND tenant_id = 0`);
    check('落库对账：小额案状态 FINISHED', r1.row?.status === 'FINISHED', r1.err ?? JSON.stringify(r1.row));
    check('落库对账：申请金额 = Σ费用明细（守恒落库）',
      Boolean(r1.row) && Math.abs(Number(r1.row.claim_amount) - TOTAL1) < 0.005,
      `db=${r1.row?.claim_amount} Σ=${TOTAL1}`);
    const r2 = mysqlRow(`SELECT status, first_reviewer_id, second_reviewer_id FROM t_claim_case WHERE id = ${globalThis.case2Claim} AND tenant_id = 0`);
    check('落库对账：大额案双人复核人落库且 ≠ 第一审核人',
      Boolean(r2.row) && r2.row.status === 'FINISHED' && r2.row.second_reviewer_id !== ''
        && r2.row.second_reviewer_id !== r2.row.first_reviewer_id,
      JSON.stringify(r2.row ?? r2.err));
    const r3 = mysqlRow(`SELECT quota_claimed FROM t_quota_account WHERE insured_id_hash = '${POLICY.insuredIdHash}' AND policy_no = '${POLICY.policyNo}' AND quota_year = YEAR(CURDATE()) AND quota_type = 'PERSONAL' AND tenant_id = 0 AND deleted = 0`);
    check('落库对账：额度账户 quota_claimed 已累计', Boolean(r3.row) && Number(r3.row.quota_claimed) > 0,
      JSON.stringify(r3.row ?? r3.err));
  }

  const summary = report('智能理赔全功能 E2E');
  if (!summary.ok) {
    console.log('失败清单：');
    for (const r of summary.results) if (!r.ok) console.log(`  · ${r.name} ${r.detail}`);
  }
  process.exit(summary.ok ? 0 : 1);
}

main().catch((e) => {
  console.error('E2E 中止：', e?.message ?? e);
  report('智能理赔全功能 E2E（中止）');
  process.exit(1);
});
