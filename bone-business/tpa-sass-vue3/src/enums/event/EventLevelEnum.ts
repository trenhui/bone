// 0：系统级，1：页面级，2：流程级，3：赔案级，4：保单级，5：发票级，6：费用项目级，7：费用项目明细级
export enum EventLevelEnum {
  system = 0,
  page = 1,
  process = 2,
  claim = 3,
  policy = 4,
  invoice = 5,
  project = 6,
  item = 7,
}

// 事件级别标签
export const EventLevelLabels = {
  [EventLevelEnum.system]: "系统级",
  [EventLevelEnum.page]: "页面级",
  [EventLevelEnum.process]: "流程级",
  [EventLevelEnum.claim]: "赔案级",
  [EventLevelEnum.policy]: "保单级",
  [EventLevelEnum.invoice]: "发票级",
  [EventLevelEnum.project]: "费用项目级",
  [EventLevelEnum.item]: "费用项目明细级",
};

// 事件级别选项
export const EventLevelOptions = [
  {
    label: EventLevelLabels[EventLevelEnum.system],
    value: EventLevelEnum.system,
  },
  {
    label: EventLevelLabels[EventLevelEnum.page],
    value: EventLevelEnum.page,
  },
  {
    label: EventLevelLabels[EventLevelEnum.process],
    value: EventLevelEnum.process,
  },
  {
    label: EventLevelLabels[EventLevelEnum.claim],
    value: EventLevelEnum.claim,
  },
  {
    label: EventLevelLabels[EventLevelEnum.policy],
    value: EventLevelEnum.policy,
  },
  {
    label: EventLevelLabels[EventLevelEnum.invoice],
    value: EventLevelEnum.invoice,
  },
  {
    label: EventLevelLabels[EventLevelEnum.project],
    value: EventLevelEnum.project,
  },
  {
    label: EventLevelLabels[EventLevelEnum.item],
    value: EventLevelEnum.item,
  },
];

// 获取事件级别标签
export const getEventLevelLabel = (value: number | EventLevelEnum) => {
  return EventLevelLabels[value as EventLevelEnum];
};
