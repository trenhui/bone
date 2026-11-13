import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// 仅替换网络服务与落库函数，保留 involvementMeta / telemetry 等真实逻辑
vi.mock('../services/aiDisclosure', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/aiDisclosure')>();
  return {
    ...actual,
    aiDisclosureApi: { disclosure: vi.fn() },
  };
});
vi.mock('../services/reviewTask', () => ({
  reviewTaskApi: { execution: vi.fn() },
}));
vi.mock('../workbench/persist', () => ({
  persistWorkbenchEvent: vi.fn(),
}));

import { AiSuggestionCard } from './AiSuggestionCard';
import { aiDisclosureApi } from '../services/aiDisclosure';
import { reviewTaskApi } from '../services/reviewTask';
import { persistWorkbenchEvent } from '../workbench/persist';

/**
 * AI 结论卡的人工裁决回流（UI 层）回归。
 *
 * 背景：真实浏览器冒烟（scripts/e2e/synap-claim-workbench-ui-smoke.mjs）只能覆盖
 * fail-closed 分支——现有 E2E 数据集里没有 involvement=AI_ASSISTED 的案件，采纳/驳回
 * 按钮不会出现。为把「真实 AI 参与时的人工裁决」这条 UI 路径也钉住，这里用组件测试
 * 直接给定 AI_ASSISTED 披露，验证：出现采纳按钮 → 点采纳 → 弹窗确定 → 落库事件被写入
 * → 出现「裁决事件已落库」提示；并反向验证非 AI_ASSISTED / 无披露时绝不展示裁决动作。
 */
const evidence = {
  graphVersion: 'g-1',
  ruleVersion: 'r-1',
  declaredModelVersion: 'm-1',
  declaredPromptVersion: 'p-1',
  reportedModelVersion: 'm-1',
  reportedPromptVersion: 'p-1',
  decisionResult: 'REJECT',
  triageReason: '',
  consumptionReason: '',
  status: 'FINISHED',
  finishedAt: null,
  traceNodeCount: 5,
  llmNodeActionResult: 'SUCCESS',
  generatedFields: [],
  rejectReasons: [],
  traceGaps: [],
};

const assistedDisclosure = {
  claimId: '776102367333449728',
  traceId: 'trace-1',
  involvement: 'AI_ASSISTED' as const,
  involvementReason: 'LLM_NODE_EXECUTED',
  label: 'AI 辅助生成（真实模型参与）',
  aiRole: 'ADVISOR',
  rights: [],
  humanFinalDecision: '最终由人工核赔人员复核',
  retentionNote: '',
  evidence,
  scopeNote: '',
};

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', data });

describe('AiSuggestionCard 人工裁决回流', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (reviewTaskApi.execution as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok({ decisionResult: 'REJECT' }),
    );
    (persistWorkbenchEvent as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      replayed: false,
    });
  });

  it('AI_ASSISTED：出现采纳按钮，点击采纳→确定后写入裁决事件并提示已落库', async () => {
    (aiDisclosureApi.disclosure as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok(assistedDisclosure),
    );

    render(<AiSuggestionCard claimId="776102367333449728" scene="case" />);

    // 采纳按钮出现（antd 两字按钮会注入空格）
    const adoptBtn = await screen.findByRole('button', { name: /^采\s*纳$/ });
    expect(adoptBtn).toBeTruthy();

    fireEvent.click(adoptBtn);
    // 弹窗打开（标题：AI 建议 · 采纳）
    await screen.findByText(/AI 建议 · 采\s*纳|AI 建议 · 采纳/);

    // antd Modal 主按钮按类名定位（避免依赖中/英 locale 的 okText 文案）
    const modalOk = document.querySelector('.ant-modal-footer .ant-btn-primary') as HTMLElement;
    expect(modalOk).toBeTruthy();
    fireEvent.click(modalOk);

    // 写入一条 DISPOSITION 事件（曝光事件也会写，故按 type 精确断言）
    await waitFor(() => {
      const events = (persistWorkbenchEvent as ReturnType<typeof vi.fn>).mock.calls.map(
        (c) => c[0],
      );
      expect(
        events.some(
          (e: { type?: string; disposition?: string }) =>
            e.type === 'ai_suggestion_disposition' && e.disposition === 'ADOPT',
        ),
      ).toBe(true);
    });

    // 明确提示已落库（而非静默或谎称）
    await screen.findByText(/裁决事件已落库/);
  });

  it('非 AI_ASSISTED：不展示采纳动作，明确 fail-closed 文案', async () => {
    (aiDisclosureApi.disclosure as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok({ ...assistedDisclosure, involvement: 'AI_UNAVAILABLE', involvementReason: 'LLM_NODE_FALLBACK' }),
    );

    render(<AiSuggestionCard claimId="776102367333449728" scene="case" />);

    await screen.findByText('当前不提供采纳 / 驳回');
    expect(screen.queryByRole('button', { name: /^采\s*纳$/ })).toBeNull();
    // 曝光事件仍会落库（它是采纳率分母），但绝不能产生任何 DISPOSITION 裁决事件
    const events = (persistWorkbenchEvent as ReturnType<typeof vi.fn>).mock.calls.map((c) => c[0]);
    expect(
      events.some((e: { type?: string }) => e.type === 'ai_suggestion_disposition'),
    ).toBe(false);
  });

  it('无披露记录：明确「暂无可判定的 AI 参与事实」，不提供裁决', async () => {
    (aiDisclosureApi.disclosure as ReturnType<typeof vi.fn>).mockResolvedValue(ok(null));

    render(<AiSuggestionCard claimId="776102367333449728" scene="case" />);

    await screen.findByText('暂无可判定的 AI 参与事实');
    expect(screen.queryByRole('button', { name: /^采\s*纳$/ })).toBeNull();
  });
});
