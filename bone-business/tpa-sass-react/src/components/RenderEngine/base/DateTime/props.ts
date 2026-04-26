import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DateFormatEnum } from "@/enums/baseComp/DateFormatEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface DateTimeProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  prompt?: string;
  placeholder?: string;
  alignment?: AlignmentTypeEnum;
  dateFormatType?: DateFormatEnum;
  earliestDatetime?: string;
  earliestDatetimeType?: number;
  latestDatetime?: string;
  latestDatetimeType?: number;
  defaultValue?: string;
  inputStatus?: InputStatusEnum;
  required?: RequiredEnum;
  displayed?: DisplayedEnum;
  isTableField?: boolean;
  showFieldLabel?: boolean;
  targetValue?: string | number;
  targetProp?: string;
  isView?: boolean;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

export const defaultDateTimeProps: Partial<DateTimeProps> = {
  name: "",
  code: "",
  showName: "",
  prompt: "",
  placeholder: "",
  alignment: AlignmentTypeEnum.Left,
  dateFormatType: DateFormatEnum.YYYYMMDD,
  earliestDatetime: "",
  earliestDatetimeType: 0,
  latestDatetime: "",
  latestDatetimeType: 0,
  defaultValue: "",
  inputStatus: InputStatusEnum.editable,
  required: RequiredEnum.notRequired,
  displayed: DisplayedEnum.show,
  isTableField: false,
  showFieldLabel: true,
  targetValue: "",
  targetProp: "",
  isView: false,
};
