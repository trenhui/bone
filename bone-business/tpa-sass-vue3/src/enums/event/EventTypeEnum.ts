export enum EventTypeEnum {
  process = 0,
  business = 1,
}

// 事件类型标签
export const EventTypeLabels = {
  [EventTypeEnum.process]: "流程事件",
  [EventTypeEnum.business]: "业务事件",
};

// 事件类型选项
export const EventTypeOptions = [
  {
    label: EventTypeLabels[EventTypeEnum.process],
    value: EventTypeEnum.process,
  },
  {
    label: EventTypeLabels[EventTypeEnum.business],
    value: EventTypeEnum.business,
  },
];

// 获取事件类型标签
export const getEventTypeLabel = (value: number | EventTypeEnum) => {
  return EventTypeLabels[value as EventTypeEnum];
};
