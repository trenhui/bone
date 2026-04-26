import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface SelectCtrlProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  alignment?: AlignmentTypeEnum;
  prompt?: string;
  placeholder?: string;
  inputStatus?: InputStatusEnum;
  defaultValue?: string;
  required?: RequiredEnum;
  displayed?: DisplayedEnum;
  selectLevel?: SelectLevelEnum;
  selectDatasource?: any;
  isTableField?: boolean;
  showFieldLabel?: boolean;
  targetValue?: string;
  targetProp?: string;
  isView?: boolean;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

export const defaultSelectCtrlProps: Partial<SelectCtrlProps> = {
  name: "",
  code: "",
  showName: "",
  alignment: AlignmentTypeEnum.Left,
  prompt: "",
  placeholder: "",
  inputStatus: InputStatusEnum.editable,
  defaultValue: "",
  required: RequiredEnum.notRequired,
  displayed: DisplayedEnum.show,
  selectLevel: SelectLevelEnum.TWO,
  selectDatasource: null,
  isTableField: false,
  showFieldLabel: true,
  targetValue: "",
  targetProp: "",
  isView: false,
};
