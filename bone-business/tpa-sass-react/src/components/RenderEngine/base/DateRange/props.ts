import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DateFormatEnum } from "@/enums/baseComp/DateFormatEnum";
import { DateRangeValueTypeEnum } from "@/enums/baseComp/DateRangeValueTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface DateRangeProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  prompt?: string;
  placeholderTwo?: string;
  placeholderThree?: string;
  alignment?: AlignmentTypeEnum;
  dateFormatType?: DateFormatEnum;
  earliestDatetimeType?: number;
  latestDatetimeType?: number;
  earliestDatetime?: string;
  latestDatetime?: string;
  defaultValue?: string;
  valueType?: DateRangeValueTypeEnum;
  inputStatus?: InputStatusEnum;
  required?: RequiredEnum;
  displayed?: DisplayedEnum;
  isTableField?: boolean;
  showFieldLabel?: boolean;
  targetValue?: string;
  targetProp?: string;
  isView?: boolean;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

export const defaultDateRangeProps: Partial<DateRangeProps> = {
  name: "",
  code: "",
  showName: "",
  prompt: "",
  placeholderTwo: "",
  placeholderThree: "",
  alignment: AlignmentTypeEnum.Left,
  dateFormatType: DateFormatEnum.YYYYMMDD,
  earliestDatetimeType: 0,
  latestDatetimeType: 0,
  earliestDatetime: "",
  latestDatetime: "",
  defaultValue: "",
  valueType: DateRangeValueTypeEnum.TEXT,
  inputStatus: InputStatusEnum.editable,
  required: RequiredEnum.required,
  displayed: DisplayedEnum.show,
  isTableField: false,
  showFieldLabel: true,
  targetValue: "",
  targetProp: "",
  isView: false,
};
