import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface InputNumProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  prompt?: string;
  placeholder?: string;
  alignment?: AlignmentTypeEnum;
  dataFormat?: DataFormatEnum;
  min?: number | string;
  max?: number | string;
  decimalDigit?: number;
  multiples?: number | string;
  inputStatus?: InputStatusEnum;
  required?: RequiredEnum;
  displayed?: DisplayedEnum;
  defaultValue?: number | string;
  isTableField?: boolean;
  showFieldLabel?: boolean;
  targetValue?: number | string;
  targetProp?: string;
  isView?: boolean;
  onChange?: (value: number | string) => void;
  onBlur?: () => void;
}

export const defaultInputNumProps: Partial<InputNumProps> = {
  name: "",
  code: "",
  showName: "",
  prompt: "",
  placeholder: "",
  alignment: AlignmentTypeEnum.Left,
  dataFormat: DataFormatEnum.number,
  min: null,
  max: null,
  decimalDigit: 0,
  multiples: 0.01,
  inputStatus: InputStatusEnum.editable,
  required: RequiredEnum.notRequired,
  displayed: DisplayedEnum.show,
  defaultValue: "",
  isTableField: false,
  showFieldLabel: true,
  targetValue: undefined,
  targetProp: "",
  isView: false,
};
