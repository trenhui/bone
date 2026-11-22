import { describe, expect, it } from 'vitest';
import { adviceErrorMessage, adviceText, ADVISORY_MANUAL_NOTICE } from './advisoryView';

describe('AI 建议态视图', () => {
  it('未知态保留原始码，不压成 AI 未参与', () => {
    expect(adviceText('AVAILABLE')).toContain('待人工终决');
    expect(adviceText('UNAVAILABLE')).toBe('AI 未参与');
    expect(adviceText('UNKNOWN')).toContain('UNKNOWN');
    expect(adviceText('UNDETERMINED')).toContain('UNDETERMINED');
    expect(adviceText(null)).toBe('无法确认');
  });

  it('失败文案按 HTTP 状态收敛，未知异常不得伪造建议态', () => {
    expect(adviceErrorMessage({ response: { status: 403 } })).toContain('403');
    expect(adviceErrorMessage({ response: { status: 404 } })).toContain('404');
    expect(adviceErrorMessage(new Error('boom'))).toBe('无法确认');
    expect(ADVISORY_MANUAL_NOTICE).toContain('最终决定由人工作出');
  });
});
