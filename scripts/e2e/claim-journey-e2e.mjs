#!/usr/bin/env node
/**
 * 智能理赔「单案全链 journey」端到端：保单配置 → 报案 → 签收 → 智能初审 →
 * 智能理算 → 复核 → 推送保司 → 打款。
 *
 * 与 claim-full-chain-e2e.mjs（7 分支广覆盖）不同，本脚本聚焦**一条案件贯穿全程**，
 * 每个环节真实 API + MySQL 第二证据源对账，任一环节断裂即判红。
 *
 * 「推送保司」= 结案（pay）同事务写一条 INSURER_PUSH 到 t_domain_event_outbox（补齐 ADR-0021
 *跨进程事件零丢失写入缺口），由 OutboxRelayJob 中继投递 PENDING→SENT；本脚本经查询端点 +
 * MySQL 双源对账，并轮询等待中继投递完成。
 *
 * 运行：node scripts/e2e/claim-journey-e2e.mjs（建议服务带 --bone.claim.schedule.outbox-older-than-minutes=0
 * 消除默认 60min 扫描窗口，使新事件当轮即可投递）
 * 退出码：0 = 全链 8 环节通过；1 = 任一环节失败。
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BASE, STAMP, login, api, makeChecker } from './e2e_lib/client.cjs';
import { dataset } from './e2e_lib/fixtures.cjs';

const { check, report } = makeChecker();
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ds = dataset(STAMP);
const sha256 = async (s) =>
  Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0')).join('');

// 种子引用数据（真源 0073）：报案/理算依赖其责任配置与额度账户。
const SEED = { policyNo: 'E2E-POLICY-0001', planId: '9001', insuredIdHash: 'e2e-insured-hash-0001' };
const NEW_POLICY_NO = `JNY-POLICY-${STAMP}`;
const OUT_INSURE = `${ds.days_ago(9)}T09:00:00Z`;
const TOTAL = ds.amount(180, 860); // 小额（<1万）单案，规避双人复核/大额资金链多身份依赖
const ITEMS = ds.split(TOTAL, 3);
const AMT = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const money = (n) => AMT.format(Number(n));

// ---------- 第二证据源：MySQL（复用 e2e_lib 唯一凭据真源） ----------
const MYSQL_PY = 'import sys,json;sys.path.insert(0,sys.argv[2]);from e2e_lib.mysql import query;' +
  'o,e=query(sys.argv[1]);print(json.dumps([o,e],ensure_ascii=False))';
function mysqlRow(sql) {
  const py = spawnSync(process.env.PYTHON || 'python3', ['-c', MYSQL_PY, sql, HERE],
    { encoding: 'utf8', timeout: 35000 });
  if (py.error || py.status !== 0) return { err: String(py.error?.message || py.stderr || `rc=${py.status}`).trim().slice(0, 200) };
  let out, err;
  try { [out, err] = JSON.parse(String(py.stdout).trim().split('\n').pop()); }
  catch { return { err: `解析失败: ${String(py.stdout).slice(0, 160)}` }; }
  if (err) return { err: String(err) };
  const lines = String(out ?? '').split('\n').filter((l) => l.trim() !== '');
  if (lines.length < 2) return { err: '无数据行' };
  const cols = lines[0].split('\t'), vals = lines[1].split('\t'), row = {};
  cols.forEach((c, i) => { row[c] = vals[i]; });
  return { row };
}

async function main() {
  console.log(`\n===== 智能理赔单案全链 journey（${BASE} · 数据集 ${STAMP}） =====\n`);
  const admin = await login(BASE);
  check('admin 真实登录（tenant 0）', Boolean(admin));

  // ========== 环节 1：保单配置（新建 → 计划 → 发布 → 激活，真实写操作） ==========
  console.log('\n--- ① 保单配置 ---');
  const effStart = `${new Date(Date.now() - 30 * 86400000).toISOString()}`.replace(/\.\d+Z$/, 'Z');
  const effEnd = `${new Date(Date.now() + 365 * 86400000).toISOString()}`.replace(/\.\d+Z$/, 'Z');
  const policyBody = {
    policyNo: NEW_POLICY_NO, policyName: `${ds.city}团体补充医疗（journey ${STAMP}）`,
    insurerName: '中国人民财产保险股份有限公司', branchName: '直属营业部',
    insureName: `${ds.company()}（journey）`, bizIdentityCode: '91310000MA1FL0B5HPJT5H', productType: 'GROUP_MEDICAL',
    effectiveStart: effStart, effectiveEnd: effEnd, waitingPeriod: 0, policyVersion: 'v1',
    insuredSnapshot: JSON.stringify([SEED.insuredIdHash]),
  };
  const cp = await api('POST', '/claim-config/policies', policyBody, { token: admin });
  check('① 新建保单 200/201 且初始 INACTIVE',
    [200, 201].includes(cp.status) && cp.data?.data?.configStatus === 'INACTIVE',
    `status=${cp.status} cfg=${cp.data?.data?.configStatus} ${JSON.stringify(cp.data).slice(0, 160)}`);
  const dbPolicy = mysqlRow(`SELECT config_status FROM t_claim_policy WHERE policy_no='${NEW_POLICY_NO}' AND tenant_id=0 AND deleted=0 LIMIT 1`);
  check('① 保单真实落库（t_claim_policy）',
    !dbPolicy.err && dbPolicy.row?.config_status === 'INACTIVE',
    `db=${JSON.stringify(dbPolicy)}`);
  const planCode = `JNY-PLAN-${STAMP}`;
  const cplan = await api('POST', '/claim-config/plans', {
    policyNo: NEW_POLICY_NO, planCode, planName: 'journey 门诊计划', planLimit: 0, waitingPeriod: 0, versionNo: 'v1',
  }, { token: admin });
  const planId = cplan.data?.data?.planId ?? cplan.data?.data?.id ?? cplan.data?.data;
  check('① 新建计划成功', [200, 201].includes(cplan.status) && Boolean(planId), `status=${cplan.status} planId=${planId}`);
  const pub = await api('POST', `/claim-config/plans/${planId}/publish`, undefined, { token: admin });
  check('① 计划发布 DRAFT→ACTIVE', pub.status === 200, `status=${pub.status} ${JSON.stringify(pub.data).slice(0, 120)}`);
  const act = await api('POST', `/claim-config/policies/${NEW_POLICY_NO}/activate`, undefined, { token: admin });
  check('① 保单激活 INACTIVE→ACTIVE', act.status === 200, `status=${act.status} ${JSON.stringify(act.data).slice(0, 120)}`);
  const dbActive = mysqlRow(`SELECT config_status FROM t_claim_policy WHERE policy_no='${NEW_POLICY_NO}' AND tenant_id=0 AND deleted=0 LIMIT 1`);
  check('① 保单激活状态落库', !dbActive.err && dbActive.row?.config_status === 'ACTIVE', `db=${JSON.stringify(dbActive)}`);

  // ========== 环节 2+3：报案 + 签收（报案即受理签收 DRAFT→7） ==========
  console.log('\n--- ② 报案 / ③ 签收（报案内含受理签收） ---');
  const submit = await api('POST', '/claims', {
    policyNo: SEED.policyNo, planId: SEED.planId, insuredIdHash: SEED.insuredIdHash,
    claimType: 'OUTPATIENT', outInsureTime: OUT_INSURE,
    items: ITEMS.map((a, i) => ({ itemName: `journey 费用${i + 1}`, category: ['SERVICE', 'EXAM', 'DRUG'][i], quantity: 1, unitPrice: a, amount: a })),
  }, { token: admin, headers: { 'Idempotency-Key': `jny-${STAMP}` } });
  const claim = submit.data?.data ?? {};
  check('② 报案 201 且返回赔案号/报案号',
    submit.status === 201 && /^CL/.test(claim.claimNo || '') && /^RP/.test(claim.reportNo || ''),
    `status=${submit.status} claimNo=${claim.claimNo} reportNo=${claim.reportNo}`);
  check('③ 签收落地：对外状态 PENDING_EVIDENCE（=已受理签收 7）',
    claim.status === 'PENDING_EVIDENCE', `status=${claim.status}`);
  const dbSign = mysqlRow(`SELECT id,status,claim_no FROM t_claim_case WHERE id=${claim.claimId} AND tenant_id=0 LIMIT 1`);
  check('③ 案件真实落库且状态对账',
    !dbSign.err && dbSign.row?.claim_no === claim.claimNo && Number(dbSign.row?.id) === Number(claim.claimId),
    `db=${JSON.stringify(dbSign)} apiClaimId=${claim.claimId}`);

  const cid = claim.claimId;
  // 登记 + 锁定证据（理算前置）
  for (const n of ['invoice', 'list']) {
    const er = await api('POST', `/claims/${cid}/evidences`, {
      fileRef: `claim/${cid}/${n}.pdf`, contentHash: await sha256(`${cid}:${n}:${STAMP}`),
      docType: n === 'list' ? 'LIST' : 'INVOICE', verifyValid: 'PASS',
    }, { token: admin, headers: { 'Idempotency-Key': `jny-ev-${cid}-${n}` } });
    if (![200, 201].includes(er.status)) throw new Error(`登记材料失败 ${n}: ${JSON.stringify(er.data)}`);
  }
  const lock = await api('POST', `/claims/${cid}/evidences/lock`, undefined, { token: admin });
  check('③ 材料证据集已登记并锁定（理算前置）', lock.status === 200, `status=${lock.status}`);

  // ========== 环节 4：智能初审（分配 → 开始初审 → 规则引擎完成 → 转人工核赔 → 领取） ==========
  console.log('\n--- ④ 智能初审 ---');
  const step = async (label, method, p, body, headers) => {
    const r = await api(method, p, body, { token: admin, headers });
    check(`④/⑤ 初审理算步骤 ${label}`, r.status === 200, `status=${r.status} ${JSON.stringify(r.data).slice(0, 120)}`);
    return r;
  };
  await step('分配初审 7→10', 'POST', `/claims/${cid}/allocate`);
  await step('开始初审 10→11', 'POST', `/claims/${cid}/pre-exam/start`, undefined, { 'X-Automated': 'false' });
  await step('初审完成 11→12（回填核定金额）', 'POST', `/claims/${cid}/pre-exam/complete`, undefined, { 'X-Approved-Amount': money(TOTAL) });
  await step('转人工核赔 12→40', 'POST', `/claims/${cid}/escalate`);
  await step('领取核赔任务 40→41', 'POST', `/claims/${cid}/adjust/claim`);
  const preDb = mysqlRow(`SELECT status FROM t_claim_case WHERE id=${cid} AND tenant_id=0 LIMIT 1`);
  check('④ 初审后案件进入核赔中（库内 41/ADJUSTING 族）',
    !preDb.err && !/PENDING_EVIDENCE/.test(preDb.row?.status || ''), `db=${JSON.stringify(preDb)}`);

  // ========== 环节 5：智能理算（规则引擎执行 + 公式哈希 + 快照 + 确认） ==========
  console.log('\n--- ⑤ 智能理算 ---');
  const adj = await api('POST', `/claims/${cid}/adjust`, undefined, { token: admin, headers: { 'Idempotency-Key': `jny-adj-${cid}` } });
  const adjData = adj.data?.data ?? {};
  check('⑤ 规则引擎理算 200（金额>0）',
    adj.status === 200 && Number(adjData.totalAmount) > 0, `amount=${adjData.totalAmount} status=${adj.status}`);
  check('⑤ 理算公式哈希+快照非空（智能理算可审计）',
    Boolean(adjData.formulaHash && adjData.snapshot), `formulaHash=${String(adjData.formulaHash).slice(0, 16)}`);
  const conf = await api('POST', `/claims/${cid}/adjust/confirm`, undefined, { token: admin });
  check('⑤ 理算确认 200', conf.status === 200, `status=${conf.status}`);

  // ========== 环节 6：复核（人工核赔通过 41→42） ==========
  console.log('\n--- ⑥ 复核（核赔审核） ---');
  const dec = await api('POST', `/claims/${cid}/decision`, {
    decision: 'APPROVED', approvedAmount: adjData.totalAmount, comment: `${ds.city} journey 全链复核通过`,
  }, { token: admin });
  check('⑥ 核赔复核通过 200（41→42）', dec.status === 200, `status=${dec.status} ${JSON.stringify(dec.data).slice(0, 120)}`);
  const decDb = mysqlRow(`SELECT status FROM t_claim_case WHERE id=${cid} AND tenant_id=0 LIMIT 1`);
  check('⑥ 复核结论落库（非核赔中）', !decDb.err && !/ADJUSTING/.test(decDb.row?.status || ''), `db=${JSON.stringify(decDb)}`);

  // ========== 环节 8：打款（结算单 → 审核硬预占 → 支付 → 结案） ==========
  console.log('\n--- ⑧ 打款（资金两段式） ---');
  const st = await api('POST', `/claims/${cid}/settlement`, { attempt: 1, accountType: 'PERSONAL' },
    { token: admin, headers: { 'Idempotency-Key': `jny-st-${cid}` } });
  check('⑧ 建结算单（软占额度，返回 settlementNo）',
    [200, 201].includes(st.status) && Boolean(st.data?.data?.settlementNo), `status=${st.status} no=${st.data?.data?.settlementNo}`);
  const ap = await api('POST', `/claims/${cid}/settlement/audit-pass`, undefined, { token: admin });
  check('⑧ 结算审核→硬预占（小额直接放行）',
    ap.status === 200 && ap.data?.data?.fundingStage === 'HARD_RESERVED',
    `status=${ap.status} stage=${ap.data?.data?.fundingStage}`);
  const pay = await api('POST', `/claims/${cid}/settlement/pay`, undefined, { token: admin, headers: { 'Idempotency-Key': `jny-pay-${cid}` } });
  check('⑧ 支付完成 200', pay.status === 200, `status=${pay.status} ${JSON.stringify(pay.data).slice(0, 120)}`);
  const finalDetail = await api('GET', `/claims/${cid}`, undefined, { token: admin });
  const finalStatus = finalDetail.data?.data?.[0]?.status;
  check('⑧ 支付后自动结案 FINISHED（库内对账）',
    finalStatus === 'FINISHED', `apiStatus=${finalStatus}`);
  const finalDb = mysqlRow(`SELECT status,claim_amount FROM t_claim_case WHERE id=${cid} AND tenant_id=0 LIMIT 1`);
  check('⑧ 结案与金额落库（申请额=明细Σ）',
    !finalDb.err && finalDb.row?.status === 'FINISHED' && Math.abs(Number(finalDb.row?.claim_amount) - TOTAL) < 0.01,
    `db=${JSON.stringify(finalDb)} expect=${money(TOTAL)}`);

  // ========== 环节 7：推送保司（结案后同事务写 Outbox → 中继投递 → 可观测） ==========
  // 顺序说明：推送事件由 pay() 在结案同事务写入，故必须在⑧打款之后验证。
  console.log('\n--- ⑦ 推送保司（结案报送保司，Outbox 零丢失） ---');
  const pushApi = await api('GET', `/claims/${cid}/insurer-push`, undefined, { token: admin });
  const push = pushApi.data?.data ?? {};
  check('⑦ 查询端点返回推送记录（非 null）',
    pushApi.status === 200 && Boolean(push.outboxId), `status=${pushApi.status} data=${JSON.stringify(pushApi.data).slice(0, 200)}`);
  check('⑦ 推送 payload 含本案赔案号/报案号/保司信息',
    (push.payload || '').includes(claim.claimNo) && (push.payload || '').includes(claim.reportNo)
      && (push.payload || '').includes('insurerCode') && (push.payload || '').includes('amount'),
    `payload=${String(push.payload).slice(0, 220)}`);
  const pushDb = mysqlRow(
    `SELECT status,retry_count,payload FROM t_domain_event_outbox WHERE id=${push.outboxId} AND tenant_id=0 LIMIT 1`);
  check('⑦ 推送事件真实落库（t_domain_event_outbox）',
    !pushDb.err && (pushDb.row?.payload || '').includes(claim.claimNo), `db=${JSON.stringify(pushDb).slice(0, 220)}`);
  // 中继每分钟一轮（older-than-minutes=0 已消除 60min 窗口）；轮询等待 SENT，最多 75 秒。
  let sentStatus = pushDb.row?.status || 'PENDING';
  for (let i = 0; i < 15 && sentStatus !== 'SENT'; i++) {
    await new Promise((r) => setTimeout(r, 5000));
    const s = mysqlRow(`SELECT status FROM t_domain_event_outbox WHERE id=${push.outboxId} AND tenant_id=0 LIMIT 1`);
    sentStatus = s.row?.status || sentStatus;
  }
  check('⑦ 中继投递完成（PENDING→SENT，事件零丢失闭环）',
    sentStatus === 'SENT', `status=${sentStatus}`);

  // ⑦ 真实 HTTP 出站第二证据源：保司接收端确实收到报文（非仅 Outbox 内部 SENT）。
  // 接收端由 tools/mock-insurer-receiver.py 启动，报文 JSONL 落盘可复核。
  const receiverBase = process.env.BONE_INSURER_RECEIVER_URI || 'http://127.0.0.1:18082';
  const recvApi = await api('GET', `${receiverBase}/received?claim_no=${encodeURIComponent(claim.claimNo)}`,
    undefined, { token: admin });
  const recvRecords = recvApi.data?.records ?? [];
  check('⑦ 保司接收端真实收到 HTTP POST（第二证据源）',
    recvApi.status === 200 && recvRecords.length >= 1,
    `status=${recvApi.status} count=${recvRecords.length} data=${JSON.stringify(recvApi.data).slice(0, 200)}`);
  const matched = recvRecords.find((r) => (r.payload?.claimNo || '') === claim.claimNo) ?? {};
  // 报送金额口径 = 理算核定/结算实付额（adjData.totalAmount，如 90% 责任），不是申请额 TOTAL。
  // 取证 SettlementApplicationService.pay()：appendOnFinish(..., amount, ...) 的 amount 即结算单实付额。
  check('⑦ 送达报文契约正确（CL/RP 号+核定实付额+事件类型）',
    matched.payload?.claimNo === claim.claimNo
      && matched.payload?.reportNo === claim.reportNo
      && Math.abs(Number(matched.payload?.amount) - Number(adjData.totalAmount)) < 0.01
      && matched.headers?.['X-Bone-Event-Type'] === 'INSURER_PUSH',
    `claimNo=${matched.payload?.claimNo} reportNo=${matched.payload?.reportNo} amount=${matched.payload?.amount} expectApproved=${adjData.totalAmount} eventType=${matched.headers?.['X-Bone-Event-Type']}`);

  // ---------- 汇总 ----------
  console.log(`\n全链案件：claimNo=${claim.claimNo} reportNo=${claim.reportNo} claimId=${cid} 申请额=${money(TOTAL)}`);
  report('智能理赔单案全链 journey');
}

main().catch((e) => { console.error('[FATAL]', e?.stack || e); process.exit(1); });
