/**
 * 原生 HTML5 拖拽排序（零新增依赖）。
 *
 * 为什么不引 dnd-kit：配置器的拖拽需求仅是"列表内/跨列表重排"，
 * 引入 40KB 运行时依赖换不来对应收益，HTML5 DnD 足够且无版本兼容风险。
 */
import type React from 'react';

export const DRAG_MIME = 'application/x-bone-ui-index';

export interface DragSortProps {
  draggable?: boolean;
  onDragStart: (e: React.DragEvent) => void;
  onDragOver: (e: React.DragEvent) => void;
  onDragLeave: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent) => void;
  onDragEnd: (e: React.DragEvent) => void;
  style?: React.CSSProperties;
}

/** 生成一个可拖拽行的事件绑定（携带自身索引） */
export function dragSort(
  index: number,
  onMove: (from: number, to: number) => void,
  onState?: (over: boolean) => void,
): DragSortProps {
  return {
    draggable: true,
    onDragStart: (e) => {
      e.dataTransfer.setData(DRAG_MIME, String(index));
      e.dataTransfer.setData('text/plain', String(index));
      e.dataTransfer.effectAllowed = 'move';
    },
    onDragOver: (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      onState?.(true);
    },
    onDragLeave: () => onState?.(false),
    onDrop: (e) => {
      e.preventDefault();
      const raw = e.dataTransfer.getData(DRAG_MIME) || e.dataTransfer.getData('text/plain');
      const from = Number(raw);
      onState?.(false);
      if (!Number.isNaN(from) && from !== index) onMove(from, index);
    },
    onDragEnd: () => onState?.(false),
  };
}

/** 跨域拖拽（分区间移动控件）用：额外携带来源分区下标 */
export function dragSort2(
  si: number,
  ci: number,
  onMove: (fromSi: number, fromCi: number, toSi: number, toCi: number) => void,
): DragSortProps {
  return {
    draggable: true,
    onDragStart: (e) => {
      e.dataTransfer.setData(DRAG_MIME, `${si}:${ci}`);
      e.dataTransfer.effectAllowed = 'move';
    },
    onDragOver: (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; },
    onDragLeave: () => undefined,
    onDrop: (e) => {
      e.preventDefault();
      const raw = e.dataTransfer.getData(DRAG_MIME);
      const [fs, fc] = String(raw).split(':').map(Number);
      if (Number.isNaN(fs) || Number.isNaN(fc)) return;
      onMove(fs, fc, si, ci);
    },
    onDragEnd: () => undefined,
  };
}
