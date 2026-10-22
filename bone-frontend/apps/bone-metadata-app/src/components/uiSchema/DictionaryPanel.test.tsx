import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DictionaryPanel from './DictionaryPanel';
import type { BoneUiSchema } from '@bone/ui-schema';
import type { MetaField } from '../../types';
import { metadataFieldOptionsApi } from '../../services/metadataApi';

vi.mock('../../services/metadataApi', () => ({
  metadataFieldOptionsApi: {
    refresh: vi.fn(),
    diff: vi.fn(),
  },
}));

/**
 * 「刷新快照」的完整语义回归（v1.1 联调实证修复）。
 *
 * 背景（2026-10-21 UI 联调探针实测）：字典面板的「刷新」按钮只更新漂移徽标
 * （diffs 是面板内部 state），而选项列表与物化时间取自宿主传入的 fields prop——
 * 刷新成功后「已漂移」徽标消失了，弃用项却仍不显示「废弃中」、物化时间不变，
 * 用户看到的是「一半刷新」。修复：刷新成功路径回调宿主 onFieldsRefetch 重取字段。
 *
 * 判据（删掉实现里的 onFieldsRefetch?.() 调用，本测试两条性质各红一条）：
 *   1. 刷新成功 ⇒ 回调宿主重取（且以字段 id 调刷新端点）；
 *   2. 刷新失败 ⇒ 不回调（失败时无事态翻转，旧快照继续展示）。
 */
const dictField = {
  id: '9001',
  code: 'label',
  name: '标签',
  displayName: '标签',
  type: 'ENUM',
  enumValues: JSON.stringify([
    { code: 'A', label: '标签A' },
    { code: 'B', label: '标签B' },
  ]),
  enumSource: JSON.stringify({
    type: 'dict',
    mode: 'snapshot',
    ref: { typeCode: 'T1' },
    resolvedAt: '2026-10-21T06:00:00Z',
    resolvedBy: 'system',
  }),
} as unknown as MetaField;

const schema = { dictionaries: {} } as unknown as BoneUiSchema;

const diffOk = { code: 200, message: 'ok', data: { resolved: true, added: [], removed: [], labelChanged: [], stateChanged: [] } };
const refreshOk = { code: 200, message: 'ok', data: { fieldId: '9001', beforeCount: 2, afterCount: 2 } };

describe('DictionaryPanel 刷新快照语义（v1.1）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(metadataFieldOptionsApi.diff).mockResolvedValue(diffOk);
  });

  it('刷新成功后回调宿主重取字段（选项/物化时间来自 fields prop，必须重取才是完整刷新）', async () => {
    vi.mocked(metadataFieldOptionsApi.refresh).mockResolvedValue(refreshOk);
    const onFieldsRefetch = vi.fn();

    render(
      <DictionaryPanel schema={schema} onChange={() => {}} fields={[dictField]} onFieldsRefetch={onFieldsRefetch} />,
    );
    fireEvent.click(screen.getByRole('button', { name: '刷新 label 的字典快照' }));

    await waitFor(() => expect(onFieldsRefetch).toHaveBeenCalledTimes(1));
    expect(metadataFieldOptionsApi.refresh).toHaveBeenCalledWith('9001');
  });

  it('负向：刷新失败不回调宿主重取（旧快照继续展示，不无事态翻转）', async () => {
    // 服务端拒绝（如真源不可达）：code 非 200，载荷保持契约形状
    vi.mocked(metadataFieldOptionsApi.refresh).mockResolvedValue({ ...refreshOk, code: 500, message: '字典服务不可达' });
    const onFieldsRefetch = vi.fn();

    render(
      <DictionaryPanel schema={schema} onChange={() => {}} fields={[dictField]} onFieldsRefetch={onFieldsRefetch} />,
    );
    fireEvent.click(screen.getByRole('button', { name: '刷新 label 的字典快照' }));

    await waitFor(() => expect(metadataFieldOptionsApi.refresh).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 0));
    expect(onFieldsRefetch).not.toHaveBeenCalled();
  });
});
