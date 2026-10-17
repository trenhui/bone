/**
 * Spotlight 交互式新手引导（对标 driver.js / Figma onboarding，步骤锚在真实 UI 上）。
 *
 * 与「静态说明 Modal」的本质区别（业界最佳实践）：
 * 1. **聚光灯定位**：遮罩挖洞 + 呼吸高亮环圈住当前步骤的真实操作点，眼睛不用找；
 * 2. **动作步（action）**：要求用户真点高亮元素（点字段库 / 点列表 tab / 点规则 tab）才算完成——
 *    看过 ≠ 会做，肌肉记忆要在第一次就建立；
 * 3. **不卡死**：每个动作步都保留「继续」兜底按钮，用户不想照做也能走完全程；
 * 4. **锚点解析限定在设计器左栏内**：实体详情页外层也有「列表显示」等 ant-tabs，
 *    全局 querySelector 会指到页面别处的 tab（探针假绿式错误，见 §13.5 踩坑）。
 *
 * 交互：→/← 切换、Esc 退出、resize/内部滚动自动重算聚光灯位置。
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Button, Space, Typography, message } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import type { TourAnchor, TourStepDef } from '../../utils/onboarding';

const { Text } = Typography;

interface Props {
  open: boolean;
  steps: TourStepDef[];
  index: number;
  onIndex: (i: number) => void;
  onClose: () => void;
}

/** 锚点 → 元素解析（限定作用域，见文件头注第 4 条） */
function resolveAnchor(anchor: TourAnchor): HTMLElement | null {
  if (anchor === 'none') return null;
  if (anchor === 'palette-fab') return document.querySelector<HTMLElement>('button[aria-label="添加字段到界面"]');
  const panel = document.querySelector<HTMLElement>('[data-tour="left-panel"]');
  if (anchor === 'left-panel') return panel;
  if (anchor === 'section') return document.querySelector<HTMLElement>('[data-tour="section"]');
  if (anchor === 'save') return document.querySelector<HTMLElement>('[data-tour="save"]');
  if (anchor === 'publish-state') return document.querySelector<HTMLElement>('[data-tour="publish-state"]');
  // tab-list / tab-rules：只认设计器左栏 tab 条里的（页面外层 ant-tabs 有「列表显示」等同名文本）
  const tabText = anchor === 'tab-list' ? '列表' : '规则';
  const tabs = panel?.querySelectorAll<HTMLElement>('.ant-tabs-tab') ?? [];
  return [...tabs].find((el) => (el.textContent ?? '').trim() === tabText) ?? null;
}

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

const PAD = 6; // 聚光灯洞比锚点大 6px，边框不压元素本身

export default function DesignerTour({ open, steps, index, onIndex, onClose }: Props): React.ReactElement | null {
  const [rect, setRect] = useState<Rect | null>(null);
  const step = steps[index];
  const last = index >= steps.length - 1;
  const cardRef = useRef<HTMLDivElement | null>(null);

  const measure = useCallback(() => {
    if (!open || !step) return;
    const el = step.anchor === 'none' ? null : resolveAnchor(step.anchor);
    if (!el) {
      setRect(null);
      return;
    }
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) {
      setRect(null);
      return;
    }
    setRect({
      top: Math.max(0, r.top - PAD),
      left: Math.max(0, r.left - PAD),
      width: Math.min(window.innerWidth, r.width + PAD * 2),
      height: Math.min(window.innerHeight, r.height + PAD * 2),
    });
  }, [open, step]);

  // 步骤切换：先滚入视口再量（锚点可能在滚动容器里）
  useLayoutEffect(() => {
    if (!open || !step || step.anchor === 'none') return;
    const el = resolveAnchor(step.anchor);
    el?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    const t = setTimeout(measure, 60);
    return () => clearTimeout(t);
  }, [open, step, measure]);

  // 布局会随用户操作变化（popover 展开 / 面板内容增减）——轮询 + resize + capture scroll 三重兜底
  useEffect(() => {
    if (!open) return;
    const iv = setInterval(measure, 250);
    window.addEventListener('resize', measure);
    window.addEventListener('scroll', measure, true);
    return () => {
      clearInterval(iv);
      window.removeEventListener('resize', measure);
      window.removeEventListener('scroll', measure, true);
    };
  }, [open, measure]);

  const go = useCallback(
    (next: number) => {
      if (next >= steps.length) onClose();
      else if (next < 0) onIndex(0);
      else onIndex(next);
    },
    [steps.length, onIndex, onClose],
  );

  // 动作步：capture 阶段监听真实点击（先于 React  Synthetic，不依赖组件树）
  useEffect(() => {
    if (!open || !step?.action) return;
    const onClick = (e: MouseEvent) => {
      const el = resolveAnchor(step.anchor);
      if (el && e.target instanceof Node && el.contains(e.target)) {
        message.success(step.doneText ?? '完成');
        go(index + 1);
      }
    };
    document.addEventListener('click', onClick, true);
    return () => document.removeEventListener('click', onClick, true);
  }, [open, step, index, go]);

  // 键盘：→ 下一步、← 上一步、Esc 退出（输入态不劫持；有弹出层时 Esc 归弹层自己——
  // 与全屏模式的 Esc 哲学一致：点 FAB 开字段库后按 Esc 关的是弹层，不是引导）
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        go(index + 1);
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        go(index - 1);
      } else if (e.key === 'Escape') {
        const popupOpen = Array.from(document.querySelectorAll(
          '.ant-modal-wrap, .ant-select-dropdown, .ant-dropdown, .ant-popover',
        )).some((el) => {
          const s = getComputedStyle(el);
          return s.display !== 'none' && s.visibility !== 'hidden';
        });
        if (popupOpen) return;
        e.preventDefault();
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, index, go, onClose]);

  if (!open || !step) return null;

  /* ---------- 聚光灯（四分区遮罩挖洞；pointer-events 全放行，不挡真实操作） ---------- */
  const spotlight = rect && (
    <>
      <div style={{ position: 'fixed', top: 0, left: 0, right: 0, height: rect.top, background: 'rgba(0,0,0,.5)', pointerEvents: 'none' }} />
      <div style={{ position: 'fixed', top: rect.top, left: 0, width: rect.left, height: rect.height, background: 'rgba(0,0,0,.5)', pointerEvents: 'none' }} />
      <div style={{ position: 'fixed', top: rect.top, left: rect.left + rect.width, right: 0, height: rect.height, background: 'rgba(0,0,0,.5)', pointerEvents: 'none' }} />
      <div style={{ position: 'fixed', top: rect.top + rect.height, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,.5)', pointerEvents: 'none' }} />
      <div
        className="tour-ring"
        style={{
          position: 'fixed', top: rect.top - 2, left: rect.left - 2,
          width: rect.width + 4, height: rect.height + 4,
          borderRadius: 10, border: '2px solid #1677ff', pointerEvents: 'none',
        }}
      />
    </>
  );

  /* ---------- 卡片定位（按 placement 贴近锚点，视口钳制不溢出） ---------- */
  const CARD_W = 340;
  const GAP = 12;
  let cardStyle: React.CSSProperties;
  if (!rect) {
    cardStyle = { left: '50%', top: '50%', transform: 'translate(-50%,-50%)' };
  } else {
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    let left: number;
    let top: number;
    if (step.placement === 'right') {
      left = rect.left + rect.width + GAP;
      top = cy - 100;
      if (left + CARD_W > vw - 8) left = rect.left - CARD_W - GAP; // 右侧不够翻左侧
    } else if (step.placement === 'left') {
      left = rect.left - CARD_W - GAP;
      top = cy - 100;
      if (left < 8) left = rect.left + rect.width + GAP;
    } else if (step.placement === 'top') {
      left = cx - CARD_W / 2;
      top = rect.top - 160 - GAP;
      if (top < 8) top = rect.top + rect.height + GAP;
    } else {
      left = cx - CARD_W / 2;
      top = rect.top + rect.height + GAP;
      if (top + 170 > vh - 8) top = rect.top - 160 - GAP;
    }
    cardStyle = {
      left: Math.max(8, Math.min(left, vw - CARD_W - 8)),
      top: Math.max(8, Math.min(top, vh - 170 - 8)),
    };
  }

  return createPortal(
    <div style={{ position: 'fixed', inset: 0, zIndex: 2100, pointerEvents: 'none' }}>
      <style>{'@keyframes tour-pulse{0%,100%{box-shadow:0 0 0 3px rgba(22,119,255,.25)}50%{box-shadow:0 0 0 8px rgba(22,119,255,.08)}}.tour-ring{animation:tour-pulse 1.6s ease-in-out infinite}.tour-chip{animation:tour-pulse 1.6s ease-in-out infinite}'}</style>
      {spotlight}
      <div
        ref={cardRef}
        className="tour-card"
        style={{
          position: 'fixed', ...cardStyle, width: CARD_W, pointerEvents: 'auto',
          background: '#fff', borderRadius: 12, boxShadow: '0 8px 28px rgba(16,24,40,.22)',
          border: '1px solid #e6eaf0', padding: '14px 16px 10px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 6 }}>
          <Text strong style={{ fontSize: 14 }}>{step.title}</Text>
          <span style={{ flex: 1 }} />
          <Text type="secondary" style={{ fontSize: 11 }}>
            {index + 1} / {steps.length}
          </Text>
          <Button type="text" size="small" icon={<CloseOutlined />} aria-label="关闭引导" onClick={onClose} style={{ marginLeft: 4 }} />
        </div>
        <div style={{ fontSize: 12, lineHeight: 1.7, color: '#000000a6', whiteSpace: 'pre-line', minHeight: 60 }}>{step.body}</div>
        {step.action && (
          <div className="tour-chip" style={{
            marginTop: 8, display: 'inline-block', padding: '3px 10px', borderRadius: 6,
            background: '#e6f4ff', color: '#1677ff', fontSize: 12, fontWeight: 600,
          }}>
            👆 点我试试 → 完成本步
          </div>
        )}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 12 }}>
          <Button type="link" size="small" style={{ paddingLeft: 0 }} onClick={onClose}>
            跳过引导
          </Button>
          <span style={{ flex: 1 }} />
          <Space size={4}>
            {steps.map((s, i) => (
              <span key={s.key} style={{
                width: i === index ? 16 : 6, height: 6, borderRadius: 3,
                background: i === index ? '#1677ff' : '#e6eaf0', transition: 'all .2s', display: 'inline-block',
              }} />
            ))}
          </Space>
          <span style={{ width: 8 }} />
          {index > 0 && (
            <Button size="small" style={{ marginRight: 6 }} onClick={() => go(index - 1)}>
              上一步
            </Button>
          )}
          <Button size="small" type="primary" onClick={() => go(index + 1)}>
            {last ? '完成' : step.action ? '继续' : '下一步'}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
