import { useEffect, useRef, useState } from 'react';

/**
 * 命令面板的全局键盘约定（Ctrl/⌘+K 开关、Esc 关闭、↑↓ 选择、Enter 执行）。
 *
 * <p>监听器只挂一次：回调用 ref 保存最新闭包，避免每次输入都解绑/重绑 keydown（既浪费也容易漏事件）。
 * 面板关闭时 ↑↓/Enter/Esc 不接管全局键盘，否则普通表单里的方向键与回车会被吞掉。
 */
export interface CommandPaletteKeyHandlers {
  /** delta = -1 上移 / +1 下移。 */
  onMove?: (delta: number) => void;
  /** 执行当前选中项。 */
  onEnter?: () => void;
}

export interface CommandPaletteControl {
  open: boolean;
  setOpen: (open: boolean) => void;
}

export function useCommandPalette(handlers: CommandPaletteKeyHandlers = {}): CommandPaletteControl {
  const [open, setOpen] = useState(false);
  const latest = useRef({ open, ...handlers });
  // 每次渲染同步最新状态/回调；事件监听器只读 ref，不随渲染重建。
  latest.current = { open, ...handlers };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen((prev) => !prev);
        return;
      }
      const state = latest.current;
      if (!state.open) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        setOpen(false);
      } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        state.onMove?.(event.key === 'ArrowDown' ? 1 : -1);
      } else if (event.key === 'Enter') {
        event.preventDefault();
        state.onEnter?.();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  return { open, setOpen };
}
