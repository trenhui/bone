/**
 * synap-claim 工作台 + AI 回流 全链路联调探针（复用 e2e_lib/client.cjs）
 *
 * 模拟人工在「我的工作台」中的真实操作，把前端实际调用的契约全部跑一遍：
 *   1. 工作台落地：claim 列表分页计数（真实待办）、review 任务计数、服务端采纳率概览；
 *   2. 跨键检索：报案号 / 保单号 / 被保人假名化ID（正向）+ 关键词过短 / limit=0（负向）；
 *   3. 案件 360：点检索命中 → 跳转案件详情；
 *   4. AI 结论卡支撑读：decision/disclosure、review/tasks/.../execution；
 *   5. AI 回流：曝光 → 采纳 / 驳回 / 修改（正向）+ 幂等重放 + 6 类负向探针；
 *   6. 列表回读 + 概览增量核对（impressions +1、dispositions +3、adopts/rejects/modifies 各 +1）。
 *
 * 真实场景数据来自既有 e2e（claim-full-chain-e2e.mjs 落地的 E2E-POLICY-0001 案件），
 * 不另造测试值；claimId 为雪花 ID（> 2^53），顺带验证 Long→String 不截断。
 *
 * 用法：node scripts/e2e/synap-claim-workbench-feedback-e2e.mjs
 *       （网关默认 127.0.0.1:8888；可 BONE_E2E_BASE 覆盖）
 */
import { api, makeChecker } from './e2e_lib/client.cjs';

const { check, report } = makeChecker();

// 真实场景数据（claim-full-chain-e2e.mjs 落地，索引见 ClaimSearchHit）
const CLAIM_ID = '776102367333449728';
const CLAIM_NO = 'CL20261112776102367333449728';
const POLICY_NO = 'E2E-POLICY-0001';
const INSURED = 'e2e-insured-hash-0001';
const ST = Date.now().toString(36); // 运行内唯一事件后缀

const ev = (suffix) => `e2e-${suffix}-${ST}`;

async function record(payload) {
  return api('POST', `/claims/${CLAIM_ID}/ai/feedback`, payload);
}

async function main() {
  // ===== 1. 工作台落地（真实计数，非 0 冒充） =====
  const pagePending = await api('GET', '/claims/?size=1&statuses=PENDING_ADJUST');
  check('工作台: claim 分页(PENDING_ADJUST) 200', pagePending.status === 200, `http=${pagePending.status}`);
  const totalPending = Number(pagePending.data?.data?.total ?? '0');
  check('工作台: 真实待办计数 > 0', totalPending > 0, `total=${totalPending}`);

  const review = await api('GET', '/review/tasks?size=1');
  check('工作台: review 任务计数 200', review.status === 200, `http=${review.status}`);
  const reviewTotal = Number(review.data?.data?.total ?? '0');
check('工作台: 审核任务计数 > 0', reviewTotal > 0, `total=${reviewTotal}`);

  const sumBefore = await api('GET', '/claims/ai/feedback/summary');
  check('工作台: 采纳率概览 200', sumBefore.status === 200, `http=${sumBefore.status}`);
  const s0 = sumBefore.data?.data ?? {};

  // ===== 2. 跨键检索（模拟工作台检索框） =====
  const searches = [
    ['报案号', CLAIM_NO],
    ['保单号', POLICY_NO],
    ['被保人假名化ID', INSURED],
  ];
  for (const [label, kw] of searches) {
    const r = await api('GET', `/claims/search?keyword=${encodeURIComponent(kw)}&limit=5`);
    check(`检索: ${label} 200`, r.status === 200, `http=${r.status} ${JSON.stringify(r.data).slice(0, 100)}`);
    const hits = r.data?.data ?? [];
    check(`检索: ${label} 命中本案件`, hits.some((h) => String(h.claimId) === CLAIM_ID), `hits=${hits.length}`);
  }
  // 负向：关键词过短（1 字，退化为全表扫描被拒）
  const shortKw = await api('GET', `/claims/search?keyword=${encodeURIComponent('测')}&limit=5`);
  check(
    '检索(负向): 1 字 400 SC_CLAIM_SEARCH_KEYWORD_INVALID',
    shortKw.status === 400 && /SC_CLAIM_SEARCH_KEYWORD_INVALID/.test(JSON.stringify(shortKw.data)),
    `http=${shortKw.status}`,
  );
  // 负向：limit=0
  const lim0 = await api('GET', `/claims/search?keyword=${encodeURIComponent(POLICY_NO)}&limit=0`);
  check('检索(负向): limit=0 400', lim0.status === 400, `http=${lim0.status}`);

  // ===== 3. 案件 360（点检索命中 → 详情） =====
  const detail = await api('GET', `/claims/${CLAIM_ID}`);
  check('案件360: 详情 200', detail.status === 200, `http=${detail.status}`);
  const detailRows = Array.isArray(detail.data?.data) ? detail.data.data : [];
  check('案件360: 详情含费用明细', detailRows.length > 0, `rows=${detailRows.length}`);

  // ===== 4. AI 结论卡支撑读（disclosure / execution） =====
  const disc = await api('GET', `/claims/${CLAIM_ID}/decision/disclosure`);
  check('AI卡: decision/disclosure 200', disc.status === 200, `http=${disc.status}`);
  const exec = await api('GET', `/review/tasks/${CLAIM_ID}/execution`);
  check('AI卡: review/tasks/.../execution 200', exec.status === 200, `http=${exec.status}`);

  // ===== 5. AI 回流：曝光 + 裁决（正向） =====
  const impression = await record({
    eventId: ev('imp'), eventType: 'IMPRESSION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case',
  });
  check('回流: 曝光 200', impression.status === 200, `http=${impression.status} ${JSON.stringify(impression.data).slice(0, 120)}`);
  check('回流: 曝光 id 为 string(防雪崩 ID 截断)', typeof impression.data?.data?.id === 'string' && impression.data.data.id.length > 0, `id=${impression.data?.data?.id}`);
  check('回流: 曝光 claimId == 原值(无截断)', String(impression.data?.data?.claimId) === CLAIM_ID, `claimId=${impression.data?.data?.claimId}`);
  check('回流: 曝光 replayed=false', impression.data?.data?.replayed === false, `replayed=${impression.data?.data?.replayed}`);

  const adopt = await record({
    eventId: ev('adopt'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'ADOPT',
  });
  check('回流: 采纳 200', adopt.status === 200, `http=${adopt.status}`);

  const reject = await record({
    eventId: ev('reject'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'REJECT', reasonCode: 'POLICY_CONFLICT',
  });
  check('回流: 驳回(带原因码) 200', reject.status === 200, `http=${reject.status}`);

  const modify = await record({
    eventId: ev('modify'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'MODIFY',
    reasonCode: 'SUGGESTION_WRONG', modifiedFields: ['decisionResult'],
  });
  check('回流: 修改(带原因码+字段) 200', modify.status === 200, `http=${modify.status}`);

  // 幂等重放：同 eventId 再发一次采纳
  const replay = await record({
    eventId: ev('adopt'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'ADOPT',
  });
  check('回流: 幂等重放 replayed=true', replay.status === 200 && replay.data?.data?.replayed === true, `replayed=${replay.data?.data?.replayed}`);

  // ===== 5b. 负向探针（fail-closed 必须有牙） =====
  const nEventId = await record({
    eventId: 'short', eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'ADOPT',
  });
  check('回流(负向): eventId 非法 400', nEventId.status === 400, `http=${nEventId.status}`);

  const nAiStub = await record({
    eventId: ev('stub'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_STUB', scene: 'case', disposition: 'ADOPT',
  });
  check('回流(负向): AI_STUB 裁决 400(非 AI_ASSISTED 不可裁决)', nAiStub.status === 400, `http=${nAiStub.status}`);

  const nAmount = await record({
    eventId: ev('amt'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'ADOPT', approvedAmount: 100,
  });
  check('回流(负向): 夹带金额字段 400(@JsonAnySetter 双保险)', nAmount.status === 400, `http=${nAmount.status}`);

  const nScene = await record({
    eventId: ev('scene'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'blind_label', disposition: 'ADOPT',
  });
  check('回流(负向): 盲标 scene 400', nScene.status === 400, `http=${nScene.status}`);

  const nRejectNoReason = await record({
    eventId: ev('rnr'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'REJECT',
  });
  check('回流(负向): REJECT 缺原因码 400', nRejectNoReason.status === 400, `http=${nRejectNoReason.status}`);

  const nModNoFields = await record({
    eventId: ev('mnf'), eventType: 'DISPOSITION', traceId: `t-${ST}`,
    involvement: 'AI_ASSISTED', scene: 'case', disposition: 'MODIFY', reasonCode: 'SUGGESTION_WRONG',
  });
  check('回流(负向): MODIFY 缺 modifiedFields 400', nModNoFields.status === 400, `http=${nModNoFields.status}`);

  // ===== 6. 列表回读 + 概览增量 =====
  const list = await api('GET', `/claims/${CLAIM_ID}/ai/feedback`);
  check('回流: 列表 200', list.status === 200, `http=${list.status}`);
  const events = list.data?.data ?? [];
  check('回流: 列表含本次 4 条(曝光/采纳/驳回/修改)', events.filter((e) => String(e.eventId).startsWith('e2e-')).length >= 4, `count=${events.length}`);
  check('回流: 列表 id 全部为 string(防截断)', events.length > 0 && events.every((e) => typeof e.id === 'string'), `n=${events.length}`);

  const sumAfter = await api('GET', '/claims/ai/feedback/summary');
  const s1 = sumAfter.data?.data ?? {};
  const delta = (k) => Number(s1[k] ?? '0') - Number(s0[k] ?? '0');
  check('概览: impressions +1', delta('impressions') === 1, `${s0.impressions}->${s1.impressions}`);
  check('概览: dispositions +3', delta('dispositions') === 3, `${s0.dispositions}->${s1.dispositions}`);
  check('概览: adopts +1', delta('adopts') === 1, `${s0.adopts}->${s1.adopts}`);
  check('概览: rejects +1', delta('rejects') === 1, `${s0.rejects}->${s1.rejects}`);
  check('概览: modifies +1', delta('modifies') === 1, `${s0.modifies}->${s1.modifies}`);
  check('概览: serverSide=true(服务端口径)', s1.serverSide === true, `serverSide=${s1.serverSide}`);

  const rep = report('synap-claim 工作台+AI回流 全链路联调');
  process.exit(rep.ok ? 0 : 1);
}

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(2);
});
