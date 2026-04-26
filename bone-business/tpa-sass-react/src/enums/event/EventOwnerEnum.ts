//0：form, 1:block, 2:tableRow, 3:tableLeftHeader, 4:tableRightHeader
export enum EventOwnerEnum {
  Form = 0,
  Block = 1,
  TableRow = 2,
  TableLeftHeader = 3,
  TableRightHeader = 4,
  Undefined = -1,
}

export const EventOwnerLabels = {
  [EventOwnerEnum.Form]: "表单",
  [EventOwnerEnum.Block]: "模块",
  [EventOwnerEnum.TableRow]: "表格行",
  [EventOwnerEnum.TableLeftHeader]: "表格左表头",
  [EventOwnerEnum.TableRightHeader]: "表格右表头",
};

export const EventOwnerOptions = [
  {
    label: "表单",
    value: EventOwnerEnum.Form,
  },
  {
    label: "模块",
    value: EventOwnerEnum.Block,
  },
  {
    label: "表格行",
    value: EventOwnerEnum.TableRow,
  },
  {
    label: "表格左表头",
    value: EventOwnerEnum.TableLeftHeader,
  },
  {
    label: "表格右表头",
    value: EventOwnerEnum.TableRightHeader,
  },
];

export const getEventOwnerLabel = (value: EventOwnerEnum) => {
  return EventOwnerLabels[value as keyof typeof EventOwnerLabels] || "";
};
