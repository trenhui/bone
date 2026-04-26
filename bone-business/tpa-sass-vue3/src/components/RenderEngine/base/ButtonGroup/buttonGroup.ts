import { DisplayTypeEnum } from "@/enums/event/DisplayTypeEnum";
import { DisplayLevelEnum } from "@/enums/event/DisplayLevelEnum";
import { EventOwnerEnum } from "@/enums/event/EventOwnerEnum";

export interface ButtonItem {
  id: string;
  label: string;
  style: DisplayLevelEnum;
  displayType: DisplayTypeEnum;
  eventId: string;
  eventName: string;
  eventCode: string;
}

export const buttonGroupProps = {
  buttonList: {
    type: Array as PropType<ButtonItem[] | null | undefined>,
    default: () => [],
  },

  align: String as PropType<"left" | "center" | "right">,

  /** 跟按钮所处的页面以及位置相关属性 */
  ownerId: String,
  owner: Number as PropType<EventOwnerEnum>,

  /** 如果是表格行中的按钮组，则需要表格行数据和表格索引 */
  isTableRow: Boolean,
  tableRow: Object as PropType<any>,
  tableIndex: Number,
};
export type ButtonGroupProps = ExtractPropTypes<typeof buttonGroupProps>;
