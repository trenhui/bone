import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProcurementWorkbenchNav, { ENTRY_DEGRADED_TEXT } from './ProcurementWorkbenchNav';

vi.mock('antd', () => ({
  Menu: ({ items, children }: { items?: Array<{ key: string; label: React.ReactNode }>; children?: React.ReactNode }) => (
    <nav>
      {(items ?? []).map((item) => <div key={item.key}>{item.label}</div>)}
      {children}
    </nav>
  ),
  Tag: ({ children }: { children?: React.ReactNode }) => <span>{children}</span>,
}));

describe('ProcurementWorkbenchNav 共享导航', () => {
  it('串接四个既有入口，不缓存判词', () => {
    render(
      <MemoryRouter>
        <ProcurementWorkbenchNav />
      </MemoryRouter>,
    );
    expect(screen.getByText(/合同人工终决/)).toBeTruthy();
    expect(screen.getByText(/人工复核/)).toBeTruthy();
    expect(screen.getByText(/AI 就绪度/)).toBeTruthy();
    expect(screen.getByText(/治理态势/)).toBeTruthy();
  });

  it('403/404 降级文案明确，不谎报入口可用', () => {
    expect(ENTRY_DEGRADED_TEXT[403]).toMatch(/权限/);
    expect(ENTRY_DEGRADED_TEXT[404]).toMatch(/未启用/);
  });
});
