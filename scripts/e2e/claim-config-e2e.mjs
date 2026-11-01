#!/usr/bin/env node
/**
 * 智能理赔 · 运营配置面 E2E（保单 / 计划 / 险种 / 额度账户）。
 *
 * 与 `claim-full-chain-e2e.mjs`（A1–A9 赔案作业面）互补：那份打「赔案从报案到打款」的主干，
 * 本份打**配置面**——保单规则、计划、险种、额度账户的建/改/状态机/并发/留痕。
 * 两者合起来才是「智能理赔」这个域的完整能力面（作业面 + 配置面）。
 *
 * 设计原则（沿用 scripts/e2e/README.md §1–§3）
 * --------------------------------------------
 * · **只走真实 API**（经网关 :8888，与前端 bone-claim-app 同口径），不直插 DB；
 *   测试数据 = 业务代码的真实产物。收尾用 **MySQL 直查做第二证据源对账**（防「接口 200 但没落库」的假绿）；
 * · **公共层唯一真源**：配置/登录/HTTP/断言取自 e2e_lib/client.cjs，造数取自 e2e_lib/fixtures.cjs；
 *   脚本内不复制 login/api/check 样板，不手写圆整金额与裸测试值；
 * · **数据生产化**：投保企业/统一社会信用代码/保单号/计划/险种/金额全部出自 fixtures
 *   （词表公司名 + 确定性信用代码 + 可读单号 + 非圆整金额），每个值只生成一次存变量、
 *   创建与断言共用；
 * · **负向探针成对存在**（README §2.5）：并发冲突 409、重复编码 409、非法责任形式 4xx、
 *   启用中改承保期间 4xx、已下线计划再发布 4xx、负额度 400、超上限 4xx；
 * · **乐观锁是本份用例的核心**：每一条写操作都带 `version` 回传，
 *   「正确版本 ⇒ 成功」「过期版本 ⇒ 409 SC_CLAIM_CONCURRENT_CONFLICT」成对断言——
 *   删掉后端 `assertEditableVersion` 一行，这几条必红（唯一能证明它真在生效的判据）。
 *
 * 运行：./scripts/e2e/run.sh claim-config-e2e.mjs
 *      （或 node scripts/e2e/claim-config-e2e.mjs）
 * 退出码：0 = 全部断言通过；1 = 任意失败。
 *
 * 残留说明：配置面**尚无删除端点**（软删除待补），故本用例造出的保单/计划/险种/额度账户
 * 留在库里。保单号带运行后缀（POL-YYYYMMDD-NNNN），可按前缀定位；这是「已知缺口」而非失控残留。
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BASE, STAMP, IAM_BASE, login, api, makeChecker } from './e2e_lib/client.cjs';
import { dataset } from './e2e_lib/fixtures.cjs';

const { check, report } = makeChecker();

const HERE = path.dirname(fileURLToPath(import.meta.url));
const MYSQL_PY =
  'import sys,json;sys.path.insert(0,sys.argv[2]);from e2e_lib.mysql import query;' +
  'o,e=query(sys.argv[1]);print(json.dumps([o,e],ensure_ascii=False))';

/** 只读 SQL 取首行（mysql -e 的 TSV：首行表头、次行数据）；失败返回 {err}，不断栈。 */
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

const P = '/claim-config';

/**
 * token 直取自 IAM（不依赖 API 根自带登录端点）：
 * 网关 :8888 与直连 :18087 都没有 /iam/login，只有 IAM :8081 才有——
 * 若让 client.cjs 用业务根去懒登录，换直连端口就会在登录处炸掉（实测）。
 * 因此本探针显式登录一次、把 token 传给每次调用，业务根可任意切换（网关 / 直连）。
 */
let TOKEN = null;
const call = (method, url, body) => api(method, url, body, { token: TOKEN });

/** 统一信封取错误码：409 等错误体的业务码在 data.errorCode（前端 resolveErrorCode 同源）。 */
const errCode = (r) => (r.data && r.data.data && r.data.data.errorCode) || '';
/** 后端 Long 全局序列化为字符串 ⇒ version 是 string，比较前统一转数字。 */
const asNum = (v) => (v === undefined || v === null || v === '' ? NaN : Number(v));
/**
 * 取列表响应里的记录行 —— **兼容两种响应契约**。
 *
 * <p>列表接口的响应形态正在演进：保单列表已是分页信封 `{records,total,page,size}`，
 * 而额度列表刚升级为分页信封，计划/险种/保全记录仍是裸数组。
 * 探针若写死 `data.data || []`，一旦后端升级契约就会 `.find is not a function` 崩在
 * 中途（看不到任何断言结果），故统一走本 helper。
 *
 * @param {object} res api() 的返回值（{status, data: ApiResponse}）
 * @returns {Array<object>} 记录行数组（无数据时为空数组）
 */
const rowsOf = (res) => {
  const d = res?.data?.data;
  if (Array.isArray(d)) return d;
  if (Array.isArray(d?.records)) return d.records;
  return [];
};

const sha256 = async (s) =>
  Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

// ================= 真实企业数据（fixtures；每个值只生成一次，创建与断言共用） =================
const ds = dataset(STAMP);
const COMPANY = ds.company();                       // 投保企业（词表公司名）
const TAX_NO = ds.tax_no();                         // 统一社会信用代码（虚构但格式合规）
const INSURER = '中国人民财产保险股份有限公司';
const BRANCH = `${ds.city}中心支公司`;
const POLICY_NO = ds.doc_no('POL');                 // POL-20261101-NNNN（可读单号）
const NAME_ONLY = '团体补充医疗保险';                // 只出现在保单名称里、不在保单号里的检索词
const PRODUCT_TYPE = 'GROUP_SUPPLEMENTARY_MEDICAL'; // 团体补充医疗保险
const START_AT = `${ds.days_ago(45)}T00:00:00Z`;   // 承保起（运行日倒推）
const END_AT = `${ds.days_ago(-365)}T00:00:00Z`;    // 承保止（一年后）
const QUOTA_YEAR = new Date().getFullYear();

/** 三层计划树：计划（限额/等待期）+ 计划下的险种（限额/责任形式）。 */
const PLANS = [
  {
    code: 'PLAN-OUTPATIENT',
    name: `${ds.dept}门急诊医疗计划`,
    limit: ds.amount(4500, 6500),
    waitingPeriod: 0,
    coverages: [
      {
        code: 'COV-OUTPATIENT-FEE',
        name: '门急诊费用报销',
        limit: ds.amount(2500, 3500),
        liabilityType: 'REIMBURSEMENT',
      },
    ],
  },
  {
    code: 'PLAN-INPATIENT',
    name: `${ds.dept}住院医疗计划`,
    limit: ds.amount(38000, 62000),
    waitingPeriod: 30,
    coverages: [
      {
        code: 'COV-INPATIENT-FEE',
        name: '住院医疗费用报销',
        limit: ds.amount(25000, 45000),
        liabilityType: 'REIMBURSEMENT',
      },
      {
        code: 'COV-INPATIENT-ALLOWANCE',
        name: '住院日额津贴',
        limit: ds.amount(80, 160),
        liabilityType: 'ALLOWANCE',
      },
    ],
  },
  {
    code: 'PLAN-CRITICAL',
    name: `${ds.dept}重大疾病额外给付计划`,
    limit: ds.amount(28000, 55000),
    waitingPeriod: 90,
    coverages: [
      {
        code: 'COV-CRITICAL-FIXED',
        name: '重大疾病保险金',
        limit: ds.amount(40000, 80000),
        liabilityType: 'FIXED_AMOUNT',
      },
    ],
  },
];

async function main() {
  console.log(`\n===== 智能理赔 · 配置面 E2E（${BASE} · 数据集 ${STAMP}） =====\n`);
  console.log(`投保企业：${COMPANY}（${TAX_NO}）`);
  console.log(`保单号：${POLICY_NO}　承保：${START_AT.slice(0, 10)} ~ ${END_AT.slice(0, 10)}\n`);

  TOKEN = await login(`${IAM_BASE}/api/v1`);
  check('管理员登录（tenant 0，IAM 直连取 token）', Boolean(TOKEN));

  // ---------- 被保人清单（假名化 ID，不存明文证件） ----------
  const insuredHashes = [];
  for (const person of [ds.employee, ds.manager, ds.person()]) {
    insuredHashes.push(await sha256(`${TAX_NO}:${person}`));
  }
  const snapshot = JSON.stringify(insuredHashes);

  // ================= 1. 保单：建 + 负向 =================
  const policyBody = {
    policyNo: POLICY_NO,
    policyName: `${COMPANY}团体补充医疗保险`,
    insurerName: INSURER,
    branchName: BRANCH,
    insureName: COMPANY,
    bizIdentityCode: TAX_NO,
    productType: PRODUCT_TYPE,
    effectiveStart: START_AT,
    effectiveEnd: END_AT,
    waitingPeriod: 30,
    policyVersion: 'v1',
    insuredSnapshot: snapshot,
  };

  const c1 = await call('POST', `${P}/policies`, policyBody);
  check('建保单 201', c1.status === 201, `实际 ${c1.status} ${JSON.stringify(c1.data).slice(0, 160)}`);
  check('保单号与投保企业回显一致',
    c1.data?.data?.policyNo === POLICY_NO && c1.data?.data?.insureName === COMPANY,
    JSON.stringify(c1.data?.data || {}).slice(0, 200));
  check('新建保单初始状态为未启用（INACTIVE）',
    c1.data?.data?.configStatus === 'INACTIVE', String(c1.data?.data?.configStatus));
  check('被保人清单已落库（3 条假名化 ID）',
    JSON.stringify(c1.data?.data?.insuredSnapshot || '').includes(insuredHashes[0].slice(0, 12)),
    String(c1.data?.data?.insuredSnapshot).slice(0, 120));

  const dup = await call('POST', `${P}/policies`, policyBody);
  check('重复保单号 → 409 SC_CLAIM_POLICY_DUPLICATED',
    dup.status === 409 && errCode(dup) === 'SC_CLAIM_POLICY_DUPLICATED',
    `实际 ${dup.status} ${errCode(dup) || JSON.stringify(dup.data).slice(0, 120)}`);

  const badPeriod = await call('POST', `${P}/policies`, {
    ...policyBody,
    policyNo: `${POLICY_NO}-BAD`,
    effectiveStart: END_AT,
    effectiveEnd: START_AT,
  });
  check('生效起晚于生效止 → 4xx（领域守卫拦截）',
    badPeriod.status >= 400 && badPeriod.status < 500,
    `实际 ${badPeriod.status} ${JSON.stringify(badPeriod.data).slice(0, 140)}`);

  const missing = await call('POST', `${P}/policies`, { policyName: '缺保单号' });
  check('缺必填字段 → 400（Bean Validation 边界拦截）',
    missing.status === 400, `实际 ${missing.status} ${JSON.stringify(missing.data).slice(0, 140)}`);

  // ================= 2. 保单：编辑 + 乐观锁 =================
  const g1 = await call('GET', `${P}/policies/${POLICY_NO}`);
  const v0 = asNum(g1.data?.data?.version);
  check('保单详情可读且带乐观锁版本号',
    g1.status === 200 && !Number.isNaN(v0), `status=${g1.status} version=${g1.data?.data?.version}`);

  const editBody = {
    policyName: `${COMPANY}团体补充医疗保险（2026年度调整版）`,
    insurerName: INSURER,
    branchName: BRANCH,
    insureName: COMPANY,
    bizIdentityCode: TAX_NO,
    productType: PRODUCT_TYPE,
    effectiveStart: START_AT,
    effectiveEnd: END_AT,
    waitingPeriod: 30,
    policyVersion: 'v2',
    insuredSnapshot: snapshot,
  };

  const e1 = await call('PUT', `${P}/policies/${POLICY_NO}`, { ...editBody, version: g1.data?.data?.version });
  check('编辑保单（带当前版本）→ 200 且名称已更新',
    e1.status === 200 && e1.data?.data?.policyName === editBody.policyName,
    `实际 ${e1.status} ${e1.data?.data?.policyName || JSON.stringify(e1.data).slice(0, 140)}`);
  check('编辑成功后版本号递增（乐观锁生效）',
    asNum(e1.data?.data?.version) > v0,
    `编辑前 ${v0} → 编辑后 ${e1.data?.data?.version}`);

  const stale = await call('PUT', `${P}/policies/${POLICY_NO}`, {
    ...editBody,
    policyName: `${COMPANY}（并发写入）`,
    version: g1.data?.data?.version, // 故意用过期版本
  });
  check('编辑保单（过期版本）→ 409 SC_CLAIM_CONCURRENT_CONFLICT',
    stale.status === 409 && errCode(stale) === 'SC_CLAIM_CONCURRENT_CONFLICT',
    `实际 ${stale.status} ${errCode(stale) || JSON.stringify(stale.data).slice(0, 140)}`);

  const g2 = await call('GET', `${P}/policies/${POLICY_NO}`);
  check('并发冲突后原值未被覆盖（写确实被拦下）',
    g2.data?.data?.policyName === editBody.policyName,
    String(g2.data?.data?.policyName));

  const legacy = await call('PUT', `${P}/policies/${POLICY_NO}`, {
    ...editBody,
    policyName: `${COMPANY}团体补充医疗保险（不传版本的兼容路径）`,
  });
  check('编辑保单（不传版本）→ 200（兼容旧客户端）',
    legacy.status === 200, `实际 ${legacy.status} ${JSON.stringify(legacy.data).slice(0, 140)}`);

  // ================= 3. 保单状态机 =================
  const act = await call('POST', `${P}/policies/${POLICY_NO}/activate`);
  check('启用保单 → 状态 ACTIVE',
    act.status === 200 && act.data?.data?.configStatus === 'ACTIVE',
    `实际 ${act.status} ${act.data?.data?.configStatus}`);

  const editActive = await call('PUT', `${P}/policies/${POLICY_NO}`, {
    ...editBody,
    effectiveStart: ds.days_ago(-30) + 'T00:00:00Z',
    version: act.data?.data?.version,
  });
  check('启用中修改承保期间 → 4xx（领域守卫：需先停用）',
    editActive.status >= 400 && editActive.status < 500,
    `实际 ${editActive.status} ${JSON.stringify(editActive.data).slice(0, 160)}`);

  const pau = await call('POST', `${P}/policies/${POLICY_NO}/pause`);
  check('停用保单 → 状态 PAUSE',
    pau.status === 200 && pau.data?.data?.configStatus === 'PAUSE',
    `实际 ${pau.status} ${pau.data?.data?.configStatus}`);

  const editPaused = await call('PUT', `${P}/policies/${POLICY_NO}`, {
    ...editBody,
    effectiveStart: ds.days_ago(-30) + 'T00:00:00Z',
    version: pau.data?.data?.version,
  });
  check('停用后可修改承保期间 → 200',
    editPaused.status === 200, `实际 ${editPaused.status} ${JSON.stringify(editPaused.data).slice(0, 140)}`);

  await call('POST', `${P}/policies/${POLICY_NO}/activate`);

  // ================= 4. 计划 =================
  const planIds = {};
  let firstPlanId = null;
  let firstPlanVersion = null;
  for (const plan of PLANS) {
    const r = await call('POST', `${P}/plans`, {
      policyNo: POLICY_NO,
      planCode: plan.code,
      planName: plan.name,
      planLimit: plan.limit,
      waitingPeriod: plan.waitingPeriod,
      versionNo: 'v1',
    });
    check(`建计划 ${plan.code} → 201`, r.status === 201, `实际 ${r.status} ${JSON.stringify(r.data).slice(0, 140)}`);
    check(`计划 ${plan.code} 初始为草稿（DRAFT）`,
      r.data?.data?.status === 'DRAFT', String(r.data?.data?.status));
    planIds[plan.code] = r.data?.data?.planId;
    if (firstPlanId === null) {
      firstPlanId = r.data?.data?.planId;
      firstPlanVersion = r.data?.data?.version;
    }
  }

  const dupPlan = await call('POST', `${P}/plans`, {
    policyNo: POLICY_NO,
    planCode: PLANS[0].code,
    planName: '重复编码',
    planLimit: 100,
    waitingPeriod: 0,
    versionNo: 'v1',
  });
  check('重复计划编码 → 409 SC_CLAIM_PLAN_DUPLICATED',
    dupPlan.status === 409 && errCode(dupPlan) === 'SC_CLAIM_PLAN_DUPLICATED',
    `实际 ${dupPlan.status} ${errCode(dupPlan) || JSON.stringify(dupPlan.data).slice(0, 140)}`);

  const planEdit = await call('PUT', `${P}/plans/${firstPlanId}`, {
    planName: `${PLANS[0].name}（调整版）`,
    planLimit: PLANS[0].limit,
    waitingPeriod: PLANS[0].waitingPeriod,
    versionNo: 'v2',
    version: firstPlanVersion,
  });
  check('编辑计划（带当前版本）→ 200 且名称已更新',
    planEdit.status === 200 && planEdit.data?.data?.planName === `${PLANS[0].name}（调整版）`,
    `实际 ${planEdit.status} ${planEdit.data?.data?.planName || JSON.stringify(planEdit.data).slice(0, 140)}`);

  const planStale = await call('PUT', `${P}/plans/${firstPlanId}`, {
    planName: `${PLANS[0].name}（并发写入）`,
    planLimit: PLANS[0].limit,
    waitingPeriod: PLANS[0].waitingPeriod,
    versionNo: 'v3',
    version: firstPlanVersion, // 过期版本
  });
  check('编辑计划（过期版本）→ 409 SC_CLAIM_CONCURRENT_CONFLICT',
    planStale.status === 409 && errCode(planStale) === 'SC_CLAIM_CONCURRENT_CONFLICT',
    `实际 ${planStale.status} ${errCode(planStale) || JSON.stringify(planStale.data).slice(0, 140)}`);

  const pub = await call('POST', `${P}/plans/${firstPlanId}/publish`);
  check('发布计划 → 状态 ACTIVE',
    pub.status === 200 && pub.data?.data?.status === 'ACTIVE',
    `实际 ${pub.status} ${pub.data?.data?.status}`);

  // ================= 5. 险种 =================
  const coverageIds = [];
  let firstCoverageId = null;
  let firstCoverageVersion = null;
  for (const plan of PLANS) {
    for (const cov of plan.coverages) {
      const r = await call('POST', `${P}/coverages`, {
        planId: planIds[plan.code],
        coverageCode: cov.code,
        coverageName: cov.name,
        coverageLimit: cov.limit,
        liabilityType: cov.liabilityType,
      });
      check(`建险种 ${cov.code} → 201`, r.status === 201, `实际 ${r.status} ${JSON.stringify(r.data).slice(0, 140)}`);
      check(`险种 ${cov.code} 责任形式为 ${cov.liabilityType}`,
        r.data?.data?.liabilityType === cov.liabilityType, String(r.data?.data?.liabilityType));
      coverageIds.push(r.data?.data?.coverageId);
      if (firstCoverageId === null) {
        firstCoverageId = r.data?.data?.coverageId;
        firstCoverageVersion = r.data?.data?.version;
      }
    }
  }

  const dupCov = await call('POST', `${P}/coverages`, {
    planId: planIds[PLANS[0].code],
    coverageCode: PLANS[0].coverages[0].code,
    coverageName: '重复编码',
    coverageLimit: 100,
    liabilityType: 'REIMBURSEMENT',
  });
  check('重复险种编码 → 409 SC_CLAIM_COVERAGE_DUPLICATED',
    dupCov.status === 409 && errCode(dupCov) === 'SC_CLAIM_COVERAGE_DUPLICATED',
    `实际 ${dupCov.status} ${errCode(dupCov) || JSON.stringify(dupCov.data).slice(0, 140)}`);

  const badLiability = await call('POST', `${P}/coverages`, {
    planId: planIds[PLANS[0].code],
    coverageCode: 'COV-BOGUS-TYPE',
    coverageName: '非法责任形式',
    coverageLimit: 100,
    liabilityType: 'NOT_A_LIABILITY_TYPE',
  });
  check('非法责任形式 → 4xx（领域守卫：只认三种责任形式）',
    badLiability.status >= 400 && badLiability.status < 500,
    `实际 ${badLiability.status} ${JSON.stringify(badLiability.data).slice(0, 160)}`);

  const covEdit = await call('PUT', `${P}/coverages/${firstCoverageId}`, {
    coverageName: `${PLANS[0].coverages[0].name}（调整版）`,
    coverageLimit: PLANS[0].coverages[0].limit,
    liabilityType: PLANS[0].coverages[0].liabilityType,
    version: firstCoverageVersion,
  });
  check('编辑险种（带当前版本）→ 200 且名称已更新',
    covEdit.status === 200 && covEdit.data?.data?.coverageName === `${PLANS[0].coverages[0].name}（调整版）`,
    `实际 ${covEdit.status} ${covEdit.data?.data?.coverageName || JSON.stringify(covEdit.data).slice(0, 140)}`);

  const covStale = await call('PUT', `${P}/coverages/${firstCoverageId}`, {
    coverageName: `${PLANS[0].coverages[0].name}（并发写入）`,
    coverageLimit: PLANS[0].coverages[0].limit,
    liabilityType: PLANS[0].coverages[0].liabilityType,
    version: firstCoverageVersion, // 过期版本
  });
  check('编辑险种（过期版本）→ 409 SC_CLAIM_CONCURRENT_CONFLICT',
    covStale.status === 409 && errCode(covStale) === 'SC_CLAIM_CONCURRENT_CONFLICT',
    `实际 ${covStale.status} ${errCode(covStale) || JSON.stringify(covStale.data).slice(0, 140)}`);

  // ================= 6. 责任配置端 =================
  // 覆盖缺口（此前为零）：责任配置端 8 个路由（create/update/publish/retire/
  // listByPlan/feeRules×2）在本 E2E 中完全没被调用过。上轮「加了仓储方法却漏了
  // SQL 模板 ⇒ 服务启动即崩」的 P0 正是从这个洞漏出来的：接口在、测试不覆盖，
  // 于是模板缺失能一路跑到线上。⇒ 这里补端到端覆盖（正 + 负）。
  const liabCode = ds.doc_no('R01');
  const liabCreate = await call('POST', `${P}/liabilities`, {
    planId: planIds[PLANS[0].code],
    coverageId: firstCoverageId,
    liabilityCode: liabCode,
    liabilityType: 'REIMBURSEMENT',
    payPercent: '90',
    liabilityDeduct: '100',
    liabilityLimit: '10000',
    dailyLimit: '500',
    dailyLimitBasis: 'SAME_DAY_INVOICE',
    limitMode: 'WHITELIST',
    limitHospitalCodes: 'H001,H002',
    catalogPolicy: 'MANDATORY',
    catalogCode: 'NATIONAL_2024',
    socialOnly: 0,
    waitingPeriod: 30,
  });
  // 注：责任配置端的 create 显式返回 HttpStatus.OK（与保单/计划的 201 不同口径），
  // 这里按控制器真实契约断言 200，不臆造 201。
  check('建责任 → 200（控制器显式 OK 口径）', liabCreate.status === 200,
    `实际 ${liabCreate.status} ${JSON.stringify(liabCreate.data).slice(0, 200)}`);
  const liabilityId = liabCreate.data?.data?.liabilityId;
  check('新建责任初始为草稿（DRAFT）',
    liabCreate.data?.data?.status === 'DRAFT', String(liabCreate.data?.data?.status));
  // **全列回读**：模板漏 SELECT ⇒ 字段恒 null（"建得进读不出"）。
  // 逐项断言业务列，杜绝"接口 200 但数据是空的"这种假绿。
  const liabView = liabCreate.data?.data ?? {};
  const liabCols = [
    ['payPercent', '90'], ['liabilityDeduct', '100'], ['liabilityLimit', '10000'],
    ['dailyLimit', '500'], ['dailyLimitBasis', 'SAME_DAY_INVOICE'],
    ['limitMode', 'WHITELIST'], ['limitHospitalCodes', 'H001,H002'],
    ['catalogPolicy', 'MANDATORY'], ['catalogCode', 'NATIONAL_2024'],
  ];
  const badCols = liabCols.filter(([k, v]) => String(liabView[k] ?? '') !== v);
  check('新建责任回读业务列齐全（模板漏 SELECT 会在此暴露）',
    badCols.length === 0,
    badCols.length === 0 ? `已核对 ${liabCols.length} 列`
      : `不一致列=${badCols.map(([k, v]) => `${k}:期望${v}/实际${liabView[k]}`).join(', ')}`);

  const liabList = await call('GET', `${P}/liabilities/plan/${planIds[PLANS[0].code]}`);
  check('按计划查责任列表 → 200 且含新建责任',
    liabList.status === 200 && rowsOf(liabList).some((r) => r.liabilityId === liabilityId),
    `实际 ${liabList.status} ${JSON.stringify(liabList.data).slice(0, 160)}`);

  // 负向：日限额无口径 ⇒ 写期自洽校验拦截（0094：把"配了不生效"拦在保存时）
  const badShape = await call('POST', `${P}/liabilities`, {
    planId: planIds[PLANS[0].code],
    coverageId: firstCoverageId,
    liabilityCode: ds.doc_no('R02'),
    liabilityType: 'REIMBURSEMENT',
    payPercent: '90',
    dailyLimit: '500',
    dailyLimitBasis: 'NONE',
  });
  check('建责任（日限额配了但口径为 NONE）→ 4xx（0094 写期自洽校验）',
    badShape.status >= 400 && badShape.status < 500,
    `实际 ${badShape.status} ${JSON.stringify(badShape.data).slice(0, 200)}`);

  // 负向：免赔额/日限额为负 ⇒ 拦截（负数会被引擎静默夹成 0 = 少扣钱）
  const negAmount = await call('POST', `${P}/liabilities`, {
    planId: planIds[PLANS[0].code],
    coverageId: firstCoverageId,
    liabilityCode: ds.doc_no('R03'),
    liabilityType: 'REIMBURSEMENT',
    payPercent: '90',
    liabilityDeduct: '-100',
  });
  check('建责任（年度免赔额为负）→ 4xx（金额非负守卫）',
    negAmount.status >= 400 && negAmount.status < 500,
    `实际 ${negAmount.status} ${JSON.stringify(negAmount.data).slice(0, 200)}`);

  // **契约固化（不是缺陷断言）**：责任额度上限为负，当前实现按「不封顶」解释
  // （LiabilityRule#liabilityUncapped：limit<=0 ⇒ 不封顶，见 assertRuleUsable
  // 注释「责任额度为负按不封顶解释」）。该语义目前**无文档背书**，是并行会话
  // 写进代码的既定契约；此处断言"行为未被静默改动"，改动该语义需同步改此断言。
  const negLimit = await call('POST', `${P}/liabilities`, {
    planId: planIds[PLANS[0].code],
    coverageId: firstCoverageId,
    liabilityCode: ds.doc_no('R05'),
    liabilityType: 'REIMBURSEMENT',
    payPercent: '90',
    liabilityLimit: '-100',
  });
  check('建责任（责任额度上限为负）→ 200 且按「不封顶」语义落库（契约固化）',
    negLimit.status === 200 && String(negLimit.data?.data?.liabilityLimit) === '-100',
    `实际 ${negLimit.status} limit=${negLimit.data?.data?.liabilityLimit}`);

  // 负向：计划不存在 ⇒ 4xx（外键完整性，不造孤儿责任）
  const badPlanLiab = await call('POST', `${P}/liabilities`, {
    planId: '999999999999999999',
    liabilityCode: ds.doc_no('R04'),
    liabilityType: 'REIMBURSEMENT',
    payPercent: '90',
  });
  check('建责任（计划不存在）→ 4xx（外键完整性）',
    badPlanLiab.status >= 400 && badPlanLiab.status < 500,
    `实际 ${badPlanLiab.status} ${JSON.stringify(badPlanLiab.data).slice(0, 160)}`);

  const liabPublish = await call('POST', `${P}/liabilities/${liabilityId}/publish`);
  check('发布责任 → 200 且状态 ACTIVE',
    liabPublish.status === 200 && liabPublish.data?.data?.status === 'ACTIVE',
    `实际 ${liabPublish.status} ${liabPublish.data?.data?.status || JSON.stringify(liabPublish.data).slice(0, 160)}`);

  const liabRetire = await call('POST', `${P}/liabilities/${liabilityId}/retire`);
  check('下线责任 → 200 且状态 RETIRED',
    liabRetire.status === 200 && liabRetire.data?.data?.status === 'RETIRED',
    `实际 ${liabRetire.status} ${liabRetire.data?.data?.status || JSON.stringify(liabRetire.data).slice(0, 160)}`);

  // 负向：已下线责任再发布 ⇒ 领域守卫拦截（不可复活）
  const liabRepublish = await call('POST', `${P}/liabilities/${liabilityId}/publish`);
  check('已下线责任再发布 → 4xx（领域守卫：不可复活）',
    liabRepublish.status >= 400 && liabRepublish.status < 500,
    `实际 ${liabRepublish.status} ${JSON.stringify(liabRepublish.data).slice(0, 160)}`);

  // ================= 7. 额度账户 =================
  // 外键完整性：不存在的计划不得开账户（否则会造出指向不存在计划的孤儿账户，且全程零报错）
  const openBadPlan = await call('POST', `${P}/quota-accounts`, {
    insuredIdHash: `${insuredHashes[0]}-fk-probe`,
    policyNo: POLICY_NO,
    planId: '999999999999999999',
    quotaYear: QUOTA_YEAR,
    quotaType: 'PERSONAL',
    quotaInitial: 1000,
    remark: '外键探针：计划不存在',
  });
  check('开户（计划不存在）→ 4xx SC_CLAIM_PLAN_NOT_FOUND',
    openBadPlan.status >= 400 && openBadPlan.status < 500 &&
      errCode(openBadPlan) === 'SC_CLAIM_PLAN_NOT_FOUND',
    `实际 ${openBadPlan.status} ${errCode(openBadPlan) || JSON.stringify(openBadPlan.data).slice(0, 140)}`);

  const quotaInitial = ds.amount(8000, 20000);
  const open = await call('POST', `${P}/quota-accounts`, {
    insuredIdHash: insuredHashes[0],
    policyNo: POLICY_NO,
    planId: planIds[PLANS[0].code],
    quotaYear: QUOTA_YEAR,
    quotaType: 'PERSONAL',
    quotaInitial,
    remark: `${COMPANY}新员工入职开户`,
  });
  check('额度开户 → 201',
    open.status === 201, `实际 ${open.status} ${JSON.stringify(open.data).slice(0, 160)}`);
  check('开户后可用额度 = 初始额度（无占用）',
    asNum(open.data?.data?.available) === asNum(quotaInitial),
    `initial=${open.data?.data?.initial} available=${open.data?.data?.available}`);
  const quotaId = open.data?.data?.quotaAccountId;
  const quotaVersion = open.data?.data?.version;

  const adjustedTo = ds.amount(6000, 15000);
  const adj = await call('POST', `${P}/quota-accounts/${quotaId}/adjust`, {
    newInitial: adjustedTo,
    remark: '年度保额下调',
    version: quotaVersion,
  });
  check('额度调额（带当前版本）→ 200 且初始额度已更新',
    adj.status === 200 && asNum(adj.data?.data?.initial) === asNum(adjustedTo),
    `实际 ${adj.status} initial=${adj.data?.data?.initial}（期望 ${adjustedTo}）`);

  const adjStale = await call('POST', `${P}/quota-accounts/${quotaId}/adjust`, {
    newInitial: ds.amount(1000, 3000),
    remark: '并发调额',
    version: quotaVersion, // 过期版本
  });
  check('额度调额（过期版本）→ 409 SC_CLAIM_CONCURRENT_CONFLICT',
    adjStale.status === 409 && errCode(adjStale) === 'SC_CLAIM_CONCURRENT_CONFLICT',
    `实际 ${adjStale.status} ${errCode(adjStale) || JSON.stringify(adjStale.data).slice(0, 140)}`);

  const qAfterStale = await call('GET', `${P}/quota-accounts?policyNo=${POLICY_NO}`);
  const accountRow = rowsOf(qAfterStale).find((a) => a.quotaAccountId === quotaId);
  check('并发冲突后额度未被改写（写确实被拦下）',
    accountRow && asNum(accountRow.initial) === asNum(adjustedTo),
    `initial=${accountRow?.initial}（期望 ${adjustedTo}）`);

  const neg = await call('POST', `${P}/quota-accounts/${quotaId}/adjust`, {
    newInitial: -1,
    remark: '负额度',
    version: adj.data?.data?.version,
  });
  check('负额度 → 400（DTO @DecimalMin 边界拦截）',
    neg.status === 400, `实际 ${neg.status} ${JSON.stringify(neg.data).slice(0, 140)}`);

  const overCap = await call('POST', `${P}/quota-accounts/${quotaId}/adjust`, {
    newInitial: 200000000,
    remark: '超上限',
    version: adj.data?.data?.version,
  });
  check('额度超上限 → 4xx（领域守卫：上限 1 亿）',
    overCap.status >= 400 && overCap.status < 500,
    `实际 ${overCap.status} ${JSON.stringify(overCap.data).slice(0, 160)}`);

  const rm = await call('POST', `${P}/quota-accounts/${quotaId}/remove`, {
    remark: '员工离职减人',
    version: adj.data?.data?.version,
  });
  check('减人 → 200 且 removed=true',
    rm.status === 200 && rm.data?.data?.removed === true,
    `实际 ${rm.status} removed=${rm.data?.data?.removed}`);

  const rmStale = await call('POST', `${P}/quota-accounts/${quotaId}/remove`, {
    remark: '并发减人',
    version: adj.data?.data?.version, // 过期版本
  });
  check('减人（过期版本）→ 409 SC_CLAIM_CONCURRENT_CONFLICT',
    rmStale.status === 409 && errCode(rmStale) === 'SC_CLAIM_CONCURRENT_CONFLICT',
    `实际 ${rmStale.status} ${errCode(rmStale) || JSON.stringify(rmStale.data).slice(0, 140)}`);

  const rs = await call('POST', `${P}/quota-accounts/${quotaId}/restore`, {
    remark: '员工复职恢复',
    version: rm.data?.data?.version,
  });
  check('恢复 → 200 且 removed=false',
    rs.status === 200 && rs.data?.data?.removed === false,
    `实际 ${rs.status} removed=${rs.data?.data?.removed}`);

  // ================= 7. 保全留痕 =================
  const changes = await call('GET', `${P}/quota-accounts/${quotaId}/changes`);
  const changeList = rowsOf(changes);
  check('保全记录可查（开户/调额/减人/恢复 ≥ 4 条）',
    changeList.length >= 4, `实际 ${changeList.length} 条`);
  const types = changeList.map((c) => c.changeType);
  check('保全记录含四种类型 INIT/ADJUST/REMOVE/RESTORE',
    ['INIT', 'ADJUST', 'REMOVE', 'RESTORE'].every((t) => types.includes(t)),
    `实际类型 ${JSON.stringify(types)}`);
  const initLog = changeList.find((c) => c.changeType === 'INIT');
  const adjustLog = changeList.find((c) => c.changeType === 'ADJUST');
  check('开户留痕 before=0（绝对值快照，非增量）',
    initLog && asNum(initLog.beforeInitial) === 0,
    `beforeInitial=${initLog?.beforeInitial}`);
  check('调额留痕 after = 当前初始额度（快照闭合）',
    adjustLog && asNum(adjustLog.afterInitial) === asNum(adjustedTo),
    `afterInitial=${adjustLog?.afterInitial}（期望 ${adjustedTo}）`);
  check('保全记录含操作人与变更说明（可追溯）',
    Boolean(adjustLog?.operatorId) && Boolean(adjustLog?.remark),
    `operatorId=${adjustLog?.operatorId} remark=${adjustLog?.remark}`);

  // ================= 8. 列表读模型（前端页面取数口径） =================
  // UI 搜索框 placeholder 承诺「保单号 / 名称」两维搜索 ⇒ 两列都必须真的命中（此前仅按保单号匹配）。
  const byNo = await call('GET', `${P}/policies?keyword=${encodeURIComponent(POLICY_NO)}&page=1&size=50`);
  check('保单列表按保单号可检索（回归原有能力）',
    rowsOf(byNo).some((r) => r.policyNo === POLICY_NO),
    `返回 ${rowsOf(byNo).length} 条`);

  const byName = await call('GET', `${P}/policies?keyword=${encodeURIComponent(NAME_ONLY)}&page=1&size=50`);
  check('保单列表按保单名称可检索（UI placeholder 承诺的两维之一）',
    rowsOf(byName).some((r) => r.policyNo === POLICY_NO),
    `返回 ${rowsOf(byName).length} 条`);

  const list = await call('GET', `${P}/policies?keyword=${encodeURIComponent(COMPANY)}&page=1&size=50`);
  const listData = list.data?.data || {};
  check('保单列表按关键字（投保企业名，含于保单名称）可检索到本用例保单',
    rowsOf(list).some((r) => r.policyNo === POLICY_NO),
    `返回 ${rowsOf(list).length} 条`);
  check('列表分页信封带 total/page/size',
    listData.total !== undefined && listData.page === 1 && listData.size === 50,
    JSON.stringify({ total: listData.total, page: listData.page, size: listData.size }));

  const planList = await call('GET', `${P}/plans?policyNo=${POLICY_NO}`);
  check('计划列表按保单可查（3 个计划）',
    rowsOf(planList).length === PLANS.length,
    `实际 ${rowsOf(planList).length}`);
  const covList = await call('GET', `${P}/coverages?planId=${planIds[PLANS[0].code]}`);
  check('险种列表按计划可查',
    rowsOf(covList).length === PLANS[0].coverages.length,
    `实际 ${rowsOf(covList).length}`);

  // ================= 9. 第二证据源：MySQL 直查对账 =================
  const policyRow = mysqlRow(
    `SELECT policy_no, policy_name, insure_name, config_status, version FROM t_claim_policy WHERE policy_no='${POLICY_NO}'`,
  );
  check('DB 直查：保单已落库且名称与接口一致',
    !policyRow.err && policyRow.row?.policy_name === editBody.policyName,
    policyRow.err || `DB policy_name=${policyRow.row?.policy_name}`);
  check('DB 直查：保单状态为 ACTIVE',
    !policyRow.err && policyRow.row?.config_status === 'ACTIVE',
    policyRow.err || `DB config_status=${policyRow.row?.config_status}`);
  // version 基线必须取「同一时刻」的接口读数：legacy 那次 PUT 之后又发生了启用/停用，
  // @Version 已继续递增；拿旧快照比对必然对不上（是探针口径错，不是实现错）。
  const freshPolicy = await call('GET', `${P}/policies/${POLICY_NO}`);
  check('DB 直查：version 与接口一致（乐观锁列真实落库）',
    !policyRow.err && asNum(policyRow.row?.version) === asNum(freshPolicy.data?.data?.version),
    policyRow.err || `DB version=${policyRow.row?.version} / 接口 version=${freshPolicy.data?.data?.version}`);

  // t_quota_account 主键是 id（quota_account_id 是 t_quota_change_log 的外键列名）；
  // 「已减人」落在 insured_state（0=在册，1=已减人），实体层面由 isRemoved() 派生。
  const quotaRow = mysqlRow(
    `SELECT id, quota_initial, quota_claimed, quota_frozen, insured_state, version FROM t_quota_account WHERE id=${quotaId}`,
  );
  check('DB 直查：额度账户初始额度与接口一致',
    !quotaRow.err && asNum(quotaRow.row?.quota_initial) === asNum(adjustedTo),
    quotaRow.err || `DB quota_initial=${quotaRow.row?.quota_initial}（期望 ${adjustedTo}）`);
  check('DB 直查：额度账户未被减人（insured_state=0）',
    !quotaRow.err && Number(quotaRow.row?.insured_state) === 0,
    quotaRow.err || `DB insured_state=${quotaRow.row?.insured_state}`);

  const logRow = mysqlRow(
    `SELECT COUNT(*) AS c FROM t_quota_change_log WHERE quota_account_id=${quotaId}`,
  );
  check('DB 直查：保全留痕行数与接口一致',
    !logRow.err && Number(logRow.row?.c) === changeList.length,
    logRow.err || `DB ${logRow.row?.c} 条 / 接口 ${changeList.length} 条`);

  // ================= 10. 计划下线状态机 =================
  const ret = await call('POST', `${P}/plans/${firstPlanId}/retire`);
  check('下线计划 → 状态 RETIRED',
    ret.status === 200 && ret.data?.data?.status === 'RETIRED',
    `实际 ${ret.status} ${ret.data?.data?.status}`);
  const republish = await call('POST', `${P}/plans/${firstPlanId}/publish`);
  check('已下线计划再发布 → 4xx（领域守卫：不可复活）',
    republish.status >= 400 && republish.status < 500,
    `实际 ${republish.status} ${JSON.stringify(republish.data).slice(0, 160)}`);

  const r = report('智能理赔 · 配置面 E2E 汇总');
  process.exit(r.ok ? 0 : 1);
}

main().catch((e) => {
  console.error('\n💥 配置面 E2E 异常终止：', e && e.stack ? e.stack : e);
  process.exit(1);
});
