// @vitest-environment node
import { describe, it, expect, beforeEach } from 'vitest';
import {
  SPACES, CARDS, FIORI_QUOTA,
  resolvePersona, resolveWorkbenchProfile, assertFioriQuota,
  type SpaceId, type WorkbenchSection,
} from './profiles';
import { PERMISSIONS } from '../constants/permissions';
import {
  CASE_TABS, CASE_TAB_META, buildCase360Link, buildCaseDeepLink,
  isSafeClaimId, isClaimIdLike, normalizeTab, parseCaseLocation, tabForPathname,
} from './caseDeepLinks';
import {
  REASON_TYPES, buildImpressionEvent, buildDispositionEvent,
  emitWorkbenchEvent, recentWorkbenchEvents, resetWorkbenchEvents,
} from './telemetry';
import { clearRecentCases, pushRecentCase, readRecentCases } from './recentCases';

const ALL_CODES = Object.values(PERMISSIONS) as string[];

function section(id: SpaceId, count: number): WorkbenchSection {
  return {
    id, label: id, description: '',
    cards: Array.from({ length: count }, (_, i) => CARDS[i % CARDS.length]),
  };
}

// ============================================================
// 一、工作台画像 / 信息架构
// ============================================================
describe('工作台画像（能力画像，非角色码）', () => {
  it('四空间按「变更节奏 × 影响半径」分组且 id 唯一', () => {
    expect(SPACES.map((s) => s.id)).toEqual(['work', 'find', 'configure', 'govern']);
    expect(new Set(SPACES.map((s) => s.id)).size).toBe(4);
  });

  it('卡片 id 唯一，且全量卡片落在 Fiori ≤25 配额内', () => {
    expect(new Set(CARDS.map((c) => c.id)).size).toBe(CARDS.length);
    expect(CARDS.length).toBeLessThanOrEqual(FIORI_QUOTA.maxCardsPerPage);
    expect(CARDS.length).toBe(20);
  });

  it('全权限画像的每个 Section 都不超 7 张卡，配额护栏通过', () => {
    const profile = resolveWorkbenchProfile(['SUPER_ADMIN'], ALL_CODES);
    expect(profile.persona).toBe('admin');
    expect(profile.source).toBe('role');
    expect(profile.sections.length).toBeGreaterThan(0);
    expect(profile.sections.length).toBeLessThanOrEqual(FIORI_QUOTA.maxSections);
    expect(assertFioriQuota(profile.sections)).toEqual([]);
  });

  it('能力画像：仅核赔写 → 核赔员，且明确标注 capability 来源', () => {
    const r = resolvePersona([], [PERMISSIONS.DECISION_WRITE, PERMISSIONS.CLAIMS_READ]);
    expect(r).toMatchObject({ persona: 'adjuster', source: 'capability' });
    expect(r.reason).toContain('核赔写');
  });

  it('多码账号按「专门岗位优先」裁定，不随顺序漂移', () => {
    const a = resolvePersona([], [PERMISSIONS.DECISION_WRITE, PERMISSIONS.INSPECTION_WRITE]);
    const b = resolvePersona([], [PERMISSIONS.INSPECTION_WRITE, PERMISSIONS.DECISION_WRITE]);
    expect(a.persona).toBe('inspection');
    expect(b.persona).toBe('inspection');
  });

  it('角色码优先于能力码（未来 IAM 下发业务角色时的扩展点）', () => {
    const r = resolvePersona(['DEMO_AUDITOR'], ALL_CODES);
    expect(r.persona).toBe('readonly');
    expect(r.source).toBe('role');
  });

  it('无任何理赔权限码 → readonly 降级，但仍有查询入口，且不含写卡', () => {
    const profile = resolveWorkbenchProfile([], []);
    expect(profile.persona).toBe('readonly');
    expect(profile.source).toBe('fallback');
    expect(profile.sections.length).toBeGreaterThan(0);
    const ids = profile.sections.flatMap((s) => s.cards.map((c) => c.id));
    expect(ids).toContain('global-search');
    expect(ids).not.toContain('todo-review');
    expect(ids).not.toContain('policy');
    expect(assertFioriQuota(profile.sections)).toEqual([]);
  });

  it('展示裁剪不是权限边界：CARDS 仍含全部卡片，隐藏卡仍可经 path 直达', () => {
    const profile = resolveWorkbenchProfile([], []);
    const visible = new Set(profile.sections.flatMap((s) => s.cards.map((c) => c.id)));
    expect(visible.size).toBeLessThan(CARDS.length);
    const hidden = CARDS.find((c) => !visible.has(c.id));
    expect(hidden?.path).toMatch(/^\//);
  });

  it('盲标金标准禁区：核赔员看不到盲标卡，复核主管可见', () => {
    const adjuster = resolveWorkbenchProfile([], [PERMISSIONS.DECISION_WRITE, PERMISSIONS.CLAIMS_READ]);
    const reviewer = resolveWorkbenchProfile([], [PERMISSIONS.REVIEW_WRITE, PERMISSIONS.CLAIMS_READ]);
    const idsOf = (p: typeof adjuster) => new Set(p.sections.flatMap((s) => s.cards.map((c) => c.id)));
    expect(idsOf(adjuster).has('blind-labels')).toBe(false);
    expect(idsOf(reviewer).has('blind-labels')).toBe(true);
  });

  it('受保护动作卡精确覆盖拒赔/大额复核/资金/后督，其余卡不得被误标', () => {
    const profile = resolveWorkbenchProfile(['SUPER_ADMIN'], ALL_CODES);
    expect([...profile.guardedCardIds].sort()).toEqual([
      'todo-dual-review', 'todo-fund', 'todo-inspection', 'todo-review',
    ]);
    expect(CARDS.filter((c) => c.guarded).length).toBe(4);
  });

  // ---- 变异验证：证明配额护栏真的有判别力，而不是恒返回 [] ----
  it('变异：单个 Section 塞 8 张卡必须判红', () => {
    expect(assertFioriQuota([section('work', 8)])).not.toEqual([]);
  });

  it('变异：6 个 Section / 整页 26 张卡 / 空页都必须判红', () => {
    expect(assertFioriQuota(Array.from({ length: 6 }, () => section('find', 3)))).not.toEqual([]);
    expect(assertFioriQuota([section('work', 7), section('find', 7), section('configure', 7), section('govern', 7)])).not.toEqual([]);
    expect(assertFioriQuota([])).not.toEqual([]);
  });

  it('反向护栏：稀疏但不为空的低权限画像不得被误判', () => {
    expect(assertFioriQuota([section('find', 1)])).toEqual([]);
  });
});

// ============================================================
// 二、案件 360 深链
// ============================================================
describe('案件 360 深链与 URL 契约', () => {
  it('claimId 白名单：只接受安全字符', () => {
    expect(isSafeClaimId('1234567890123456789')).toBe(true);
    expect(isSafeClaimId('claim-ABC_1')).toBe(true);
    for (const bad of ['', '  ', '1/2', '1?tab=x', '1#x', '1 2', 'a'.repeat(65), null, undefined]) {
      expect(isSafeClaimId(bad as string | null)).toBe(false);
    }
  });

  it('isClaimIdLike 只认纯数字雪花号（与 URL 安全性解耦，防把保单号当 claimId 跳 400）', () => {
    // 真实雪花号（18–19 位纯数字）→ true，直接跳案件 360
    expect(isClaimIdLike('776102367333449728')).toBe(true);
    expect(isClaimIdLike('1234567890123456789')).toBe(true);
    // 保单号 / 报关号（带字母）/ 11 位手机号 / 短数字 → false，必须走后端跨键检索
    for (const notId of [
      'E2E-POLICY-0001',
      'CL20261112776102367333449728',
      '13800138000',
      '1001',
      'claim-ABC_1',
      '',
      '   ',
      null,
      undefined,
    ]) {
      expect(isClaimIdLike(notId as string | null)).toBe(false);
    }
  });

  it('从案件 360 到作业页的链接自动带 claimId（首次补齐深链）', () => {
    expect(buildCaseDeepLink('1001', 'material')).toBe('/evidence?claimId=1001');
    expect(buildCaseDeepLink('1001', 'fund')).toBe('/fund?claimId=1001');
    expect(buildCaseDeepLink('1001', 'trace')).toBe('/review?claimId=1001');
  });

  it('概览没有独立作业页，生成案件 360 自身链接', () => {
    expect(buildCaseDeepLink('1001', 'overview')).toBe('/case?claimId=1001&tab=overview');
    expect(buildCase360Link('1001', 'adjust')).toBe('/case?claimId=1001&tab=adjust');
  });

  it('非法 claimId 绝不生成链接（防路径穿越 / 参数注入）', () => {
    expect(buildCaseDeepLink('../../admin', 'material')).toBeNull();
    expect(buildCase360Link('1&role=admin', 'fund')).toBeNull();
    expect(buildCaseDeepLink('', 'material')).toBeNull();
  });

  it('未知 tab 一律回退 overview，不落到任意路径', () => {
    expect(normalizeTab('drop_table')).toBe('overview');
    expect(buildCaseDeepLink('1001', 'drop_table' as never)).toBe('/case?claimId=1001&tab=overview');
    expect(CASE_TABS).toHaveLength(8);
  });

  it('parse 正常解析并标记非法 tab 回退（界面须提示，不能假装没事）', () => {
    expect(parseCaseLocation('?claimId=1001&tab=material')).toEqual({
      claimId: '1001', tab: 'material', tabRecovered: false,
    });
    const recovered = parseCaseLocation('?claimId=1001&tab=bogus');
    expect(recovered.tab).toBe('overview');
    expect(recovered.tabRecovered).toBe(true);
    expect(parseCaseLocation('?claimId=1/2').claimId).toBeNull();
  });

  it('6 个手输 ID 的作业页都能反查到对应 Tab（CaseIdBar 一处改动全覆盖）', () => {
    expect(tabForPathname('/evidence')).toBe('material');
    expect(tabForPathname('/adjust')).toBe('adjust');
    expect(tabForPathname('/fund')).toBe('fund');
    expect(tabForPathname('/appeal')).toBe('appeal');
    expect(tabForPathname('/suspend')).toBe('suspend');
    expect(tabForPathname('/inspection')).toBe('inspection');
    expect(tabForPathname('/unknown')).toBe('overview');
    expect(tabForPathname('/evidence/')).toBe('material');
  });

  it('每个 Tab 的目标页都在微应用内且带受保护标记的资金/挂起/后督不可批量', () => {
    for (const tab of CASE_TABS) expect(CASE_TAB_META[tab].targetPath).toMatch(/^\/[a-z-]+$/);
    expect(CASE_TAB_META.fund.guarded).toBe(true);
    expect(CASE_TAB_META.suspend.guarded).toBe(true);
    expect(CASE_TAB_META.inspection.guarded).toBe(true);
    expect(CASE_TAB_META.material.guarded).toBeUndefined();
  });

  // ---- 3.2 内嵌视图后的 URL 契约：分享 / 前进后退 / 18 位雪花 ID 逐字不丢 ----
  it('深链分享：18 位雪花 ID 与 tab 逐字往返（绝不 Number 化 / 科学计数法）', () => {
    const id = '776102367333449728';
    const link = buildCase360Link(id, 'fund');
    expect(link).toBe('/case?claimId=776102367333449728&tab=fund');
    if (!link) throw new Error('案件深链生成失败');
    const parsed = parseCaseLocation(link.replace('/case', ''));
    expect(parsed).toEqual({ claimId: id, tab: 'fund', tabRecovered: false });
    expect(parsed.claimId).toHaveLength(18);
    expect(buildCaseDeepLink(id, 'material')).toBe('/evidence?claimId=776102367333449728');
    expect(buildCase360Link(id, 'inspection')).toBe('/case?claimId=776102367333449728&tab=inspection');
  });

  it('浏览器前进后退：URL 序列解析出的 tab 与 claimId 一一对应，不串案', () => {
    const id = '776102367333449728';
    const history = [
      `?claimId=${id}&tab=overview`,
      `?claimId=${id}&tab=material`,
      `?claimId=${id}&tab=fund`,
      `?claimId=${id}&tab=inspection`,
    ];
    expect(history.map((s) => parseCaseLocation(s))).toEqual([
      { claimId: id, tab: 'overview', tabRecovered: false },
      { claimId: id, tab: 'material', tabRecovered: false },
      { claimId: id, tab: 'fund', tabRecovered: false },
      { claimId: id, tab: 'inspection', tabRecovered: false },
    ]);
    // 回退后再前进落在同一 claimId，且不残留上一个 tab
    expect(parseCaseLocation(history[1]).tab).toBe('material');
    expect(parseCaseLocation(history[3]).tab).toBe('inspection');
  });

  it('未登记 tab / 路径穿越值一律回退 overview 并标记可见提示', () => {
    for (const bad of ['../../admin', 'drop_table', '%2F..%2Fadmin', 'fund;drop', 'FUND']) {
      const parsed = parseCaseLocation(`?claimId=1001&tab=${bad}`);
      expect(parsed.tab).toBe('overview');
      expect(parsed.tabRecovered).toBe(true);
      expect(buildCaseDeepLink('1001', bad as never)).toBe('/case?claimId=1001&tab=overview');
    }
  });

  it('非法 claimId 落安全空态：不保留 ID、可保留已登记的 tab', () => {
    for (const bad of ['1/2', '1 2', '1?x=1', 'a'.repeat(65), '<script>']) {
      expect(parseCaseLocation(`?claimId=${encodeURIComponent(bad)}&tab=fund`)).toEqual({
        claimId: null, tab: 'fund', tabRecovered: false,
      });
      expect(buildCase360Link(bad, 'fund')).toBeNull();
    }
  });
});

// ============================================================
// 三、AI 建议采纳回流
// ============================================================
describe('AI 建议曝光 / 裁决事件', () => {
  beforeEach(() => resetWorkbenchEvents());

  it('原因码与后端 ReasonType 四值逐字对齐，不得私自扩充', () => {
    expect([...REASON_TYPES]).toEqual(['RULE_MISS', 'CONTEXT_MISS', 'SUGGESTION_WRONG', 'POLICY_CONFLICT']);
  });

  it('曝光事件是采纳率的分母，且明确标注未落库', () => {
    const r = buildImpressionEvent({ claimId: '1001', traceId: 't-1', involvement: 'AI_ASSISTED' });
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.event.type).toBe('ai_suggestion_impression');
    expect(r.event.persisted).toBe(false);
    expect(r.event.backendContract).toBe('pending');
  });

  it('负向：盲标禁区拒绝曝光，防反馈环污染金标准', () => {
    const r = buildImpressionEvent({ claimId: '1001', scene: 'blind_label' });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error).toContain('禁区');
  });

  it('负向：非法 claimId 拒绝生成埋点', () => {
    expect(buildImpressionEvent({ claimId: '1/2' }).ok).toBe(false);
    expect(buildDispositionEvent({ claimId: '1/2', disposition: 'ADOPT' }).ok).toBe(false);
  });

  it('负向：REJECT / MODIFY 必须选受控原因码，自由文本不可替代', () => {
    const reject = buildDispositionEvent({ claimId: '1001', disposition: 'REJECT', comment: '我觉得不对' });
    expect(reject.ok).toBe(false);
    if (!reject.ok) expect(reject.error).toContain('原因码');
    expect(buildDispositionEvent({ claimId: '1001', disposition: 'MODIFY' }).ok).toBe(false);
  });

  it('负向：未登记原因码被拒，且 ADOPT 无需原因码', () => {
    expect(buildDispositionEvent({ claimId: '1001', disposition: 'REJECT', reasonCode: 'BECAUSE' }).ok).toBe(false);
    const adopt = buildDispositionEvent({ claimId: '1001', disposition: 'ADOPT' });
    expect(adopt.ok).toBe(true);
  });

  it('合规：驳回事件带原因码与备注，但事件体永不含金额字段（AI 不碰钱）', () => {
    const r = buildDispositionEvent({
      claimId: '1001', disposition: 'REJECT', reasonCode: 'RULE_MISS', comment: '既往症漏判',
    });
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.event.reasonCode).toBe('RULE_MISS');
    expect(r.event.comment).toBe('既往症漏判');
    const keys = Object.keys(r.event);
    for (const forbidden of ['amount', 'approvedAmount', 'decisionAmount', 'settlementAmount']) {
      expect(keys).not.toContain(forbidden);
    }
  });

  it('备注有长度上限，避免自由文本变成第二个回流管道', () => {
    const r = buildDispositionEvent({
      claimId: '1001', disposition: 'REJECT', reasonCode: 'CONTEXT_MISS', comment: 'x'.repeat(500),
    });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.event.comment?.length).toBe(200);
  });

  it('事件总线：广播后可在最近事件里读到，缓冲有上限且可重置', () => {
    const r = buildImpressionEvent({ claimId: '1001' });
    if (!r.ok) throw new Error('unexpected');
    expect(emitWorkbenchEvent(r.event)).toBe(false); // node 环境无 window，明确返回 false
    expect(recentWorkbenchEvents()).toHaveLength(1);
    for (let i = 0; i < 80; i += 1) emitWorkbenchEvent(r.event);
    expect(recentWorkbenchEvents().length).toBe(50);
    resetWorkbenchEvents();
    expect(recentWorkbenchEvents()).toEqual([]);
  });
});

// ============================================================
// 四、最近访问（会话级便利功能，必须可降级）
// ============================================================
describe('最近访问案件', () => {
  it('node / 隐私模式无 sessionStorage 时返回空数组而不是抛异常', () => {
    expect(readRecentCases()).toEqual([]);
    expect(() => clearRecentCases()).not.toThrow();
  });

  it('写入不抛异常；无存储时返回空列表，主流程不被便利功能拖垮', () => {
    expect(() => pushRecentCase('1001', 'material')).not.toThrow();
    expect(readRecentCases()).toEqual([]);
  });
});
