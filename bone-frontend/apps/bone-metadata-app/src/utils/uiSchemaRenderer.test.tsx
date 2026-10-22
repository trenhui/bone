import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import type { BoneUiSchema } from '@bone/ui-schema';
import { SchemaRenderer } from '@bone/ui-schema';

// 最小内联 schema（规范形态：内联分区对象），覆盖动作/金额/加载态
const baseSchema = {
  schemaVersion: 1,
  entityCode: 't',
  namespace: 't',
  views: {
    form: {
      sections: [
        {
          id: 's1',
          title: '基本信息',
          controls: [
            { fieldCode: 'amt', controlKind: 'MONEY' },
            { fieldCode: 'name', controlKind: 'INPUT' },
          ],
        },
      ],
    },
  },
  actionBar: [{ actionCode: 'execute', sortOrder: 1, label: '执行理算' }],
} as unknown as BoneUiSchema;

describe('SchemaRenderer', () => {
  it('#2 动作按钮显示 label 而非裸 actionCode', () => {
    render(<SchemaRenderer schema={baseSchema} data={{ amt: 0, name: 'x' }} onAction={() => {}} />);
    expect(screen.getByText('执行理算')).toBeTruthy();
  });

  it('#3 金额由 controlKind=MONEY 驱动（不再按字段名嗅探）', () => {
    render(<SchemaRenderer schema={baseSchema} data={{ amt: 1234.5, name: '张三' }} />);
    // antd Statistic 把 1,234.50 拆成整/小两部分，分别断言
    expect(screen.getByText('¥')).toBeTruthy();
    expect(screen.getByText('1,234')).toBeTruthy();
    expect(screen.getByText('.50')).toBeTruthy();
  });

  it('#1 畸形 schema（非法分区引用）触发 onError 上抛', async () => {
    const bad = {
      ...baseSchema,
      views: { form: { sections: ['missing'] as unknown as never[] } },
    } as unknown as BoneUiSchema;
    const onError = vi.fn();
    render(<SchemaRenderer schema={bad} data={{}} onError={onError} />);
    await waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onError.mock.calls[0][0].length).toBeGreaterThan(0);
  });

  it('#7 loading 时不渲染分区内容；非 loading 时渲染', () => {
    const { rerender } = render(
      <SchemaRenderer schema={baseSchema} data={{ amt: 0, name: 'x' }} loading />,
    );
    expect(screen.queryByText('基本信息')).toBeNull();
    rerender(<SchemaRenderer schema={baseSchema} data={{ amt: 0, name: 'x' }} />);
    expect(screen.getByText('基本信息')).toBeTruthy();
  });
});
