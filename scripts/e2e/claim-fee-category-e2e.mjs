#!/usr/bin/env node
/**
 * 智能理赔 · 费用类别规则进入理算 专项 E2E（0094 规则 → 0095 明细 fee_code → 引擎消费）。
 *
 * 为什么单独一份（与 claim-config-e2e / claim-full-chain-e2e 的分工）
 * ----------------------------------------------------------------
 * 那两份打的是「配置面 CRUD 与状态机」「报案到打款主干」，都不回答一个问题：
 * **配了「手术费 100%、材料费 60% 且限 500」之后，同一张发票的理算结果到底变没变？**
 * 单测（AdjustmentEngineTest#FeeCategoryRuleConsumption）已证明引擎内部口径，
 * 但单测用的是手搓 Line，不经过 DTO → 实体 → 持久化 → 读回 → 引擎这条真实链路。
 * 本份补的正是这一层：缺 fee_code 列、SQL 漏列、DTO 丢字段、saveAll 未落库，
 * 任一环节断了单测都不会红，只有真实 API + 真实库会红。
 *
 * 断言设计（双向，缺一不可）
 * ----------------------------------------------------------------
 * A 向：配规则（手术费 100% / 材料费 60% 限 500）→ 报案三笔明细 → 理算
 *       期望 1286.40 + min(974.60×60%, 500) + 832.10×90% = 2535.29
 * B 向：**撤掉规则**（全量替换为空）→ 同一案件重算
 *       期望 (1286.40 + 974.60 + 832.10) × 90% = 2783.79（= 责任统一比例，引入规则前的行为）
 * 两向结果必须不同：只断言 A 向等于某个数，无法排除「规则根本没生效、引擎凑巧算出同值」；
 * 同一案件在同一进程里改规则重算能同时证明「规则被读到」和「规则被撤掉后确实不再生效」。
 *
 * 第三证据：明细 fee_code 必须真落库（t_claim_item.fee_code），
 * 且理算行快照 input_snapshot 要带 feeCode / ratioPercent / cappedBy——
 * 只对总金额等于某数，仍可能是「别的行凑出来的」；逐行归因才说明钱是按哪个类别、哪个比例算的。
 *
 * 运行（需 claim 服务与库可用；默认直连旁路实例 18091，跑共享栈时用 BONE_E2E_CLAIM_BASE 指向网关）
 *   ./scripts/e2e/run.sh claim-fee-category-e2e.mjs
 *   BONE_E2E_CLAIM_BASE=http://127.0.0.1:8888/api/v1 node scripts/e2e/claim-fee-category-e2e.mjs
 * 退出码：0 = 全绿；1 = 任一断言失败（失败清单见输出）。
 *
 * 残留与清理：**责任费用规则属于共享种子责任（LIAB-E2E-9001-0001）**，
 * 留在库里会改变其它 E2E / 人工联调的期望值（claim-full-chain 假定 90%）。
 * 故本脚本在 finally 里无条件把规则还原为「运行前的快照」，异常退出也还原。
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { IAM_BASE, BASE, STAMP, login, api, makeChecker } from './e2e_lib/client.cjs';

const { check, report } = makeChecker();

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** claim 服务根：默认旁路实例（不动共享栈），可按需指向网关 */
const CLAIM_API = (process.env.BONE_E2E_CLAIM_BASE || 'http://127.0.0.1:18091/api/v1').replace(/\/$/, '');
/** token 由 IAM 直发（claim 服务本身没有 /iam/login），再带去 claim 服务——与前端同口径的 JWT */
const LOGIN_BASE = `${IAM_BASE}/api/v1`;

/** 种子引用数据（真源 scripts/migration/0073_seed_claim_reference_data.sql） */
const POLICY = { policyNo: 'E2E-POLICY-0001', planId: 9001, insuredIdHash: 'e2e-insured-hash-0001' };
/** 种子责任统一比例：90%（t_adj_liability.pay_percent），用作「规则未生效」的对照基线 */
const LIABILITY_PERCENT = 90;

const MYSQL_PY =
  'import sys,json;sys.path.insert(0,sys.argv[2]);from e2e_lib.mysql import query;' +
  'o,e=query(sys.argv[1]);print(json.dumps([o,e],ensure_ascii=False))';

/** 只读 SQL，返回 TSV 文本（首行表头）；失败返回 {err}。 */
function mysqlRaw(sql) {
  const py = spawnSync(process.env.PYTHON || 'python3', ['-c', MYSQL_PY, sql, HERE], {
    encoding: 'utf8',
    timeout: 35000,
  });
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
  if (err) return { err: String(err).replace(/mysql: \[Warning\][^\n]*/g, '').trim() };
  return { rows: String(out ?? '').split('\n').filter((l) => l.trim() !== '') };
}

/** 取首行数据的对象形态（表头 → 值）。 */
function mysqlRow(sql) {
  const r = mysqlRaw(sql);
  if (r.err) return { err: r.err };
  if (!r.rows || r.rows.length < 2) return { err: '无数据行' };
  const head = r.rows[0].split('\t');
  const vals = r.rows[1].split('\t');
  return { row: Object.fromEntries(head.map((h, i) => [h, vals[i]])) };
}

const num = (v) => (v === null || v === undefined || v === '' ? NaN : Number(v));
const money = (v) => Number(Number(v).toFixed(2));

/** 带 claim 服务的 token 调用（token 从 IAM 取） */
async function call(method, p, body, opts = {}) {
  return api(method, p, body, { ...opts, base: CLAIM_API, loginBase: LOGIN_BASE });
}

const sha256 = async (s) =>
  Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

/** 登记并锁定证据集（理算前置：未锁定 → 422） */
async function registerAndLockEvidence(token, claimId) {
  for (const n of ['invoice', 'fee-list']) {
    const r = await call(
      'POST',
      `/claims/${claimId}/evidences`,
      { fileRef: `claim/${claimId}/${n}.pdf`, contentHash: await sha256(`${claimId}:${n}:${STAMP}`), docType: n === 'fee-list' ? 'LIST' : 'INVOICE', verifyValid: 'PASS' },
      { token, headers: { 'Idempotency-Key': `fc-ev-${claimId}-${n}` } },
    );
    if (r.status !== 201 && r.status !== 200) throw new Error(`登记材料失败(${n}): ${JSON.stringify(r.data).slice(0, 200)}`);
  }
  const lock = await call('POST', `/claims/${claimId}/evidences/lock`, undefined, { token });
  if (lock.status !== 200) throw new Error(`锁定证据失败: ${JSON.stringify(lock.data).slice(0, 200)}`);
}

/** 受理 → 分配 → 初审 → 转人工 → 领取（41），返回时案件可理算 */
async function driveToAdjusting(token, claimId, approvedAmount) {
  const steps = [
    ['allocate', () => call('POST', `/claims/${claimId}/allocate`, undefined, { token })],
    ['pre-exam/start', () => call('POST', `/claims/${claimId}/pre-exam/start`, undefined, { token, headers: { 'X-Automated': 'false' } })],
    ['pre-exam/complete', () => call('POST', `/claims/${claimId}/pre-exam/complete`, undefined, { token, headers: { 'X-Approved-Amount': String(approvedAmount) } })],
    ['escalate', () => call('POST', `/claims/${claimId}/escalate`, undefined, { token })],
    ['adjust/claim', () => call('POST', `/claims/${claimId}/adjust/claim`, undefined, { token })],
  ];
  for (const [name, fn] of steps) {
    const r = await fn();
    if (r.status !== 200) throw new Error(`${name} 失败: ${r.status} ${JSON.stringify(r.data).slice(0, 200)}`);
  }
}

/** 逐行归因：解析 t_adj_calculation_line.input_snapshot，按 feeCode 建索引（只看未被软删的行，与 API 读路径同口径） */
function linesByFeeCode(calculationId) {
  const r = mysqlRaw(
    `SELECT input_snapshot, fee_calculate, ratio_payout, fee_payout FROM t_adj_calculation_line ` +
      `WHERE calculation_id=${calculationId} AND line_type='OUTPUT' AND deleted=0`,
  );
  if (r.err) return { err: r.err };
  const rows = (r.rows || []).slice(1);
  const map = new Map();
  for (const line of rows) {
    const [snapRaw, feeCalc, ratioPayout, feePayout] = line.split('\t');
    let snap = {};
    try {
      snap = JSON.parse(snapRaw);
    } catch {
      return { err: `快照不是合法 JSON: ${String(snapRaw).slice(0, 120)}` };
    }
    const key = snap.feeCode || '';
    if (!map.has(key)) map.set(key, []);
    map.get(key).push({ snap, feeCalc: num(feeCalc), ratioPayout: num(ratioPayout), feePayout: num(feePayout) });
  }
  return { map };
}

async function main() {
  const token = await login(LOGIN_BASE);
  console.log(`>> claim 服务: ${CLAIM_API}\n>> 登录: ${LOGIN_BASE}`);

  // ============ 0. 定位种子责任（共享数据，改写前先拍快照，finally 还原） ============
  const liab = await call('GET', `/claim-config/liabilities/plan/${POLICY.planId}`, undefined, { token });
  const active = (liab.data?.data ?? []).find((x) => x.status === 'ACTIVE');
  check('种子责任存在且 ACTIVE（E2E 依赖它的 90% 统一比例）', !!active, `实际 ${liab.status} ${JSON.stringify(liab.data).slice(0, 200)}`);
  if (!active) throw new Error('缺少 ACTIVE 责任，无法继续');
  // 视图主键叫 liabilityId（不是 id）；规则接口的入参比例/限额是字符串（BigDecimal 由服务端解析）
  const liabilityId = active.liabilityId;

  const before = await call('GET', `/claim-config/liabilities/${liabilityId}/fee-rules`, undefined, { token });
  check('读取责任费用规则（改动前）→ 200', before.status === 200, `实际 ${before.status} ${JSON.stringify(before.data).slice(0, 200)}`);
  const snapshotBefore = (Array.isArray(before.data?.data) ? before.data.data : []).map((r) => ({
    feeCode: r.feeCode,
    payPercent: r.payPercent,
    limitAmount: r.limitAmount,
    socialOnly: r.socialOnly,
  }));
  console.log(`   改动前规则: ${snapshotBefore.length === 0 ? '(空)' : JSON.stringify(snapshotBefore)}`);

  try {
    // ============ 1. 配规则：手术费 100%、材料费 60% 且限 500 ============
    const RULES = [
      { feeCode: 'SURGERY_FEE', payPercent: '100', limitAmount: '0', socialOnly: 0 },
      { feeCode: 'MATERIAL_FEE', payPercent: '60', limitAmount: '500', socialOnly: 0 },
    ];
    const saved = await call('PUT', `/claim-config/liabilities/${liabilityId}/fee-rules`, { items: RULES }, { token });
    check('保存费用类别规则 → 200 且回显 2 条', saved.status === 200 && (saved.data?.data ?? []).length === 2,
      `实际 ${saved.status} ${JSON.stringify(saved.data).slice(0, 240)}`);

    const reread = await call('GET', `/claim-config/liabilities/${liabilityId}/fee-rules`, undefined, { token });
    const rules = reread.data?.data ?? [];
    check('规则可读回：手术费 100% 无限额', rules.some((r) => r.feeCode === 'SURGERY_FEE' && num(r.payPercent) === 100),
      JSON.stringify(rules).slice(0, 240));
    check('规则可读回：材料费 60% 限额 500', rules.some((r) => r.feeCode === 'MATERIAL_FEE' && num(r.payPercent) === 60 && num(r.limitAmount) === 500),
      JSON.stringify(rules).slice(0, 240));

    const dbRules = mysqlRaw(`SELECT COUNT(*) AS c FROM t_adj_liability_fee_rule WHERE liability_uuid='LIAB-E2E-9001-0001'`);
    check('DB 直查：规则真落库 2 行', !dbRules.err && num((dbRules.rows?.[1] ?? '').trim()) === 2, dbRules.err || `实际 ${dbRules.rows?.[1]}`);

    // ---- 负向探针：目录库里不存在的类别编码必须被拦（保存时校验，不是理算时静默失配） ----
    const badCode = await call('PUT', `/claim-config/liabilities/${liabilityId}/fee-rules`,
      { items: [{ feeCode: 'NOT_IN_CATALOG_X', payPercent: '100' }] }, { token });
    check('负向：不存在的费用类别编码 → 4xx', badCode.status >= 400 && badCode.status < 500,
      `实际 ${badCode.status} ${JSON.stringify(badCode.data).slice(0, 200)}`);
    const dupCode = await call('PUT', `/claim-config/liabilities/${liabilityId}/fee-rules`,
      { items: [{ feeCode: 'SURGERY_FEE', payPercent: '100' }, { feeCode: 'SURGERY_FEE', payPercent: '50' }] }, { token });
    check('负向：重复类别编码 → 4xx', dupCode.status >= 400 && dupCode.status < 500,
      `实际 ${dupCode.status} ${JSON.stringify(dupCode.data).slice(0, 200)}`);

    // 负向探针会全量替换？不会——校验在写之前失败，规则应仍是 2 条（顺带验证非破坏性）
    const afterNeg = await call('GET', `/claim-config/liabilities/${liabilityId}/fee-rules`, undefined, { token });
    check('负向探针失败后规则仍为 2 条（校验先于写入，非破坏性）', (afterNeg.data?.data ?? []).length === 2,
      JSON.stringify(afterNeg.data).slice(0, 200));

    // ============ 2. 报案：三笔明细，费用类别分别命中「有规则/有规则有上限/无规则」 ============
    const items = [
      { itemName: '手术费-腹腔镜胆囊切除术', category: 'SERVICE', quantity: 1, unitPrice: 1286.4, amount: 1286.4, department: '普外科', feeCode: 'SURGERY_FEE' },
      { itemName: '材料费-一次性吻合器', category: 'MATERIAL', quantity: 1, unitPrice: 974.6, amount: 974.6, department: '普外科', feeCode: 'MATERIAL_FEE' },
      { itemName: '检查费-上腹增强CT', category: 'EXAM', quantity: 1, unitPrice: 832.1, amount: 832.1, department: '影像科', feeCode: 'EXAM_FEE' },
    ];
    const TOTAL_SUBMIT = money(items.reduce((s, i) => s + i.amount, 0)); // 3093.10

    const submit = await call('POST', '/claims',
      { ...POLICY, claimType: 'OUTPATIENT', outInsureTime: `${new Date(Date.now() - 9 * 86400000).toISOString().slice(0, 10)}T09:00:00Z`, items },
      { token, headers: { 'Idempotency-Key': `fc-${STAMP}-${Math.random().toString(16).slice(2, 8)}` } });
    check('报案（明细带 feeCode）→ 201', submit.status === 201, `实际 ${submit.status} ${JSON.stringify(submit.data).slice(0, 240)}`);
    const claimId = submit.data?.data?.claimId;
    if (!claimId) throw new Error('报案未返回 claimId');

    const dbItems = mysqlRaw(`SELECT fee_code, amount FROM t_claim_item WHERE claim_id=${claimId} ORDER BY amount DESC`);
    const dbFeeCodes = (dbItems.rows ?? []).slice(1).map((l) => l.split('\t')[0]).sort();
    check('DB 直查：明细 fee_code 真落库（SURGERY_FEE / MATERIAL_FEE / EXAM_FEE）',
      !dbItems.err && JSON.stringify(dbFeeCodes) === JSON.stringify(['EXAM_FEE', 'MATERIAL_FEE', 'SURGERY_FEE']),
      dbItems.err || `实际 ${JSON.stringify(dbFeeCodes)}`);

    // ============ 3. 推进到可理算并执行理算 ============
    await registerAndLockEvidence(token, claimId);
    await driveToAdjusting(token, claimId, TOTAL_SUBMIT);

    const adj = await call('POST', `/claims/${claimId}/adjust`, undefined, { token, headers: { 'Idempotency-Key': `fc-adj-${claimId}` } });
    check('理算执行 → 200', adj.status === 200, `实际 ${adj.status} ${JSON.stringify(adj.data).slice(0, 240)}`);
    const res = adj.data?.data ?? {};
    const calcId = res.calculationId;

    // 期望：手术费 1286.40×100% + 材料费 min(974.60×60%=584.76, 500) + 检查费 832.10×90%=748.89
    const EXP_WITH_RULES = money(1286.4 * 1.0 + Math.min(974.6 * 0.6, 500) + 832.1 * 0.9); // 2535.29
    // 对照：若无类别规则，全部按责任 90% —— 引入规则前的行为
    const EXP_NO_RULES = money(TOTAL_SUBMIT * (LIABILITY_PERCENT / 100)); // 2783.79

    check(`理算总额 = ${EXP_WITH_RULES}（手术费 100% + 材料费封顶 500 + 检查费 90%）`,
      money(res.totalAmount) === EXP_WITH_RULES, `实际 ${res.totalAmount}（无规则应为 ${EXP_NO_RULES}）`);
    check('理算总额 ≠ 无规则基线（证明规则确实改变了结果，而非凑巧同值）',
      money(res.totalAmount) !== EXP_NO_RULES, `实际 ${res.totalAmount}`);

    // ---- 回归守卫：初审回填核定金额会「读实体 → 改 → 整实体写回」，
    //      读 SQL 的列清单漏列就会把刚落的 fee_code/department 悄悄抹成空串
    //      （本轮真实踩到：findByClaimId.sql 漏 fee_code → 规则读不到 → 理算按统一比例算） ----
    const afterBackfill = mysqlRaw(`SELECT fee_code, department FROM t_claim_item WHERE claim_id=${claimId}`);
    const kept = (afterBackfill.rows ?? []).slice(1);
    check('理算后 fee_code 仍在（写回未抹列：findByClaimId.sql 列清单须覆盖实体列）',
      !afterBackfill.err && kept.length === 3 && kept.every((l) => (l.split('\t')[0] || '') !== ''),
      afterBackfill.err || JSON.stringify(kept));
    check('理算后 department 仍在（同类回归：行级「次」分组维度不许丢失）',
      !afterBackfill.err && kept.length === 3 && kept.every((l) => (l.split('\t')[1] || '') !== ''),
      afterBackfill.err || JSON.stringify(kept));

    // ---- 逐行归因：钱是按哪个类别、哪个比例、有没有被限额截断算出来的 ----
    const byFee = linesByFeeCode(calcId);
    check('DB 直查：理算行可按 feeCode 归因', !byFee.err && (byFee.map?.size ?? 0) >= 3, byFee.err || `类别数 ${byFee.map?.size}`);

    const surgery = byFee.map?.get('SURGERY_FEE')?.[0];
    check('逐行：手术费行比例 100%（覆盖责任统一 90%）',
      !!surgery && num(surgery.snap.ratioPercent) === 100 && money(surgery.feePayout) === 1286.4,
      JSON.stringify(surgery ?? {}).slice(0, 240));

    const material = byFee.map?.get('MATERIAL_FEE')?.[0];
    check('逐行：材料费行比例 60%', !!material && num(material.snap.ratioPercent) === 60,
      JSON.stringify(material ?? {}).slice(0, 240));
    check('逐行：材料费按类别限额截断到 500（584.76 → 500）',
      !!material && money(material.feePayout) === 500 && String(material.snap.cappedBy || '').includes('FEE_CATEGORY'),
      JSON.stringify(material ?? {}).slice(0, 240));

    const exam = byFee.map?.get('EXAM_FEE')?.[0];
    check('逐行：未配置规则的检查费回落责任 90%',
      !!exam && num(exam.snap.ratioPercent) === 90 && money(exam.feePayout) === 748.89,
      JSON.stringify(exam ?? {}).slice(0, 240));
    check('逐行：未配置规则的检查费未被限额标记',
      !!exam && !String(exam.snap.cappedBy || '').includes('FEE_CATEGORY'),
      JSON.stringify(exam ?? {}).slice(0, 240));

    // ============ 4. B 向：撤规则 → 同案重算必须回到 90% 基线 ============
    const cleared = await call('PUT', `/claim-config/liabilities/${liabilityId}/fee-rules`, { items: [] }, { token });
    check('清空费用类别规则 → 200 且 0 条', cleared.status === 200 && (cleared.data?.data ?? []).length === 0,
      `实际 ${cleared.status} ${JSON.stringify(cleared.data).slice(0, 200)}`);

    const re = await call('POST', `/claims/${claimId}/adjust/recalculate`, undefined, { token, headers: { 'X-Rule-Version': '' } });
    check('重算 → 200', re.status === 200, `实际 ${re.status} ${JSON.stringify(re.data).slice(0, 240)}`);
    check(`撤规则后重算 = ${EXP_NO_RULES}（全部回落 90%，规则确实不再生效）`,
      money(re.data?.data?.totalAmount) === EXP_NO_RULES, `实际 ${re.data?.data?.totalAmount}`);

    const byFee2 = linesByFeeCode(re.data?.data?.calculationId);
    const allNinety = [...(byFee2.map?.values() ?? [])].flat().every((l) => num(l.snap.ratioPercent) === LIABILITY_PERCENT);
    check('撤规则后逐行比例全为 90%', !byFee2.err && allNinety, byFee2.err || '存在非 90% 的行');
    // 重算必须替换上一轮归因，而不是叠加：同一 calculation_id 的在用行数应恰为 3（三笔明细）
    const liveLines = [...(byFee2.map?.values() ?? [])].flat().length;
    check('重算替换旧归因（在用行数恰为 3，无叠加；旧行已软删）', !byFee2.err && liveLines === 3,
      byFee2.err || `在用行数 ${liveLines}`);

    const formulaChanged = res.formulaHash && re.data?.data?.formulaHash && res.formulaHash !== re.data?.data?.formulaHash;
    check('规则变更后 formulaHash 变化（同案同输入、不同规则 ⇒ 不同指纹）', !!formulaChanged,
      `before=${res.formulaHash} after=${re.data?.data?.formulaHash}`);
  } finally {
    // ============ 5. 还原共享责任规则（异常也要还原，否则污染其它 E2E 的期望值） ============
    try {
      const restore = await call('PUT', `/claim-config/liabilities/${liabilityId}/fee-rules`, { items: snapshotBefore }, { token });
      const back = await call('GET', `/claim-config/liabilities/${liabilityId}/fee-rules`, undefined, { token });
      const now = (back.data?.data ?? []).length;
      console.log(`\n[清理] 责任 ${liabilityId} 规则已还原为 ${now} 条（原 ${snapshotBefore.length} 条），HTTP ${restore.status}`);
    } catch (e) {
      console.error(`\n[清理] 还原规则失败，需人工处理：责任 ${liabilityId}，期望 ${JSON.stringify(snapshotBefore)}`, e?.message);
    }
  }

  const r = report('智能理赔 · 费用类别规则进入理算 E2E 汇总');
  process.exit(r.ok ? 0 : 1);
}

main().catch((e) => {
  console.error('\n💥 费用类别规则 E2E 异常终止：', e && e.stack ? e.stack : e);
  process.exit(1);
});
