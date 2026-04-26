
import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { InputValueTypeEnum } from "@/enums/baseComp/InputValueTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface InputProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  alignment?: number;
  required?: number;
  displayed?: number;
  prompt?: string;
  placeholder?: string;
  limitedLength?: number;
  inputStatus?: number;
  valueType?: number;
  defaultValue?: string;
  showFieldLabel?: boolean;
  targetValue?: string;
  targetProp?: string;
  isTableField?: boolean;
  isView?: boolean;
}

export const defaultInputProps: Partial<InputProps> = {
  name: "",
  code: "",
  showName: "",
  alignment: AlignmentTypeEnum.Left,
  required: RequiredEnum.notRequired,
  displayed: DisplayedEnum.show,
  prompt: "",
  placeholder: "",
  limitedLength: 100,
  inputStatus: InputStatusEnum.editable,
  valueType: InputValueTypeEnum.TEXT,
  defaultValue: "",
  showFieldLabel: true,
  targetValue: "",
  targetProp: "",
  isTableField: false,
  isView: false,
};
