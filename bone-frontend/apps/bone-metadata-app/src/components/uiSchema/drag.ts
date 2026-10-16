/**
 * 原生 HTML5 拖拽重排（零新增依赖）。
 *
 * 为什么不引 dnd-kit：配置器的拖拽需求仅是"列表内/跨列表重排"，
 * 引入 40KB 运行时依赖换不来对应收益，HTML5 DnD 足够且无版本兼容风险。
 *
 * 历史说明：此前的 `dragSort`（列表内重排 + onState 高亮）随结构面板演进为
 * 跨分区模型后已无调用方（grep 实证），按死代码纪律删除；高亮能力合并入
 * `dragSort2` 的可选 onState 回调，避免两份漂移的实现。
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

/**
 * 跨分区拖拽（分区间移动控件）用：额外携带来源分区下标。
 *
 * @param onState 落点高亮回调（true=拖拽悬停本行 / false=离开或结束）——
 *   调用方以 functional setState 实现「仅当当前高亮正是自己时才清除」，
 *   防止迟滞的 dragleave（从 A 行慢速滑向 B 行时 A 的 leave 晚于 B 的 over 到达）
 *   把 B 的高亮误清。见 StructurePanel rowOver。
 * @note onDragOver 调 stopPropagation：行级悬停不应再冒泡触发分区体高亮，
 *   两者同时亮会让「落点是哪」变得含糊；drop 不停止冒泡——分区体仍要接收
 *   字段库拖入（F: 载荷）与分区重排（S 载荷）。
 */
export function dragSort2(
  si: number,
  ci: number,
  onMove: (fromSi: number, fromCi: number, toSi: number, toCi: number) => void,
  onState?: (over: boolean) => void,
): DragSortProps {
  return {
    draggable: true,
    onDragStart: (e) => {
      e.dataTransfer.setData(DRAG_MIME, `${si}:${ci}`);
      e.dataTransfer.effectAllowed = 'move';
    },
    onDragOver: (e) => {
      e.preventDefault();
      e.stopPropagation();
      e.dataTransfer.dropEffect = 'move';
      onState?.(true);
    },
    onDragLeave: () => onState?.(false),
    onDrop: (e) => {
      e.preventDefault();
      const raw = e.dataTransfer.getData(DRAG_MIME);
      const [fs, fc] = String(raw).split(':').map(Number);
      onState?.(false);
      if (!Number.isNaN(fs) && !Number.isNaN(fc) && (fs !== si || fc !== ci)) {
        onMove(fs, fc, si, ci);
      }
    },
    onDragEnd: () => onState?.(false),
  };
}
