// 导入所需内容
import { SimpleEventBus as ImportedSimpleEventBus, MicroAppMessenger, getEventBus, globalEventBus } from './micro-app-messenger';
import type { MicroAppMessage } from './micro-app-messenger';

// 重新导出
export type { MicroAppMessage };
export { MicroAppMessenger, getEventBus, globalEventBus };
export const SimpleEventBus = ImportedSimpleEventBus;

// 基础应用事件类型（简化版，用于兼容性）
export interface AppEvents {
  [eventName: string]: unknown;
}

// 为兼容性保留getGlobalEventBus函数
export function getGlobalEventBus(): ImportedSimpleEventBus {
  return globalEventBus;
}
