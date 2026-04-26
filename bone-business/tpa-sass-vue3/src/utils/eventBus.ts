import mitt from "mitt";

type Events = {
  /**处理关联表格初始化联动 */
  [key: `table:${string}:currentChange`]: {
    currentRow: Record<string, any>;
  };
  /**处理关联表格联动规则 */
  [key: `table:${string}:linkageChange`]: Record<string, any>[];
  /**处理确认签收 */
  event_sign_confirm: void;
  /**处理刷新表格数据 */
  [key: `table:${string}:refresh`]: void;
  /**处理刷新赔案数据 */
  [key: `claim:${string}:refresh`]: void;
  /**处理导入人员信息 */
  "claim:importCollectPersonInfo": Record<string, any>;
};

export const eventBus = mitt<Events>();

class EventBus {
  static emit<K extends keyof Events>(type: K, data: Events[K]) {
    eventBus.emit(type, data);
  }

  static on<K extends keyof Events>(
    type: K,
    handler: (data: Events[K]) => void
  ) {
    eventBus.on(type, handler);
  }

  static off<K extends keyof Events>(
    type: K,
    handler: (data: Events[K]) => void
  ) {
    eventBus.off(type, handler);
  }
}

export default EventBus;
