// 触发机制
export enum TriggerEnum {
  //手动触发
  manual = 0,
  //定时触发
  timed = 1,
  //规则触发
  rule = 2,
}

// 触发机制标签
export const TriggerLabels = {
  [TriggerEnum.manual]: "手动触发",
  [TriggerEnum.timed]: "定时触发",
  [TriggerEnum.rule]: "规则触发",
};

// 触发机制选项
export const TriggerOptions = [
  { label: TriggerLabels[TriggerEnum.manual], value: TriggerEnum.manual },
  { label: TriggerLabels[TriggerEnum.timed], value: TriggerEnum.timed },
  { label: TriggerLabels[TriggerEnum.rule], value: TriggerEnum.rule },
];

// 获取触发机制标签
export const getTriggerLabel = (value: number | TriggerEnum) => {
  return TriggerLabels[value as TriggerEnum];
};
