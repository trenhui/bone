
import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { FilterTypeEnum } from "@/enums/baseComp/FilterTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export interface SelectDropProps {
  id: string;
  name?: string;
  code?: string;
  showName?: string;
  dataBinding?: string;
  alignment?: number;
  prompt?: string;
  placeholder?: string;
  inputStatus?: number;
  defaultValue?: string;
  required?: number;
  displayed?: number;
  extraConfig?: any;
  selectType?: number;
  filterType?: number;
  selectDatasource?: any;
  showFieldLabel?: boolean;
  targetValue?: any;
  targetProp?: string;
  isView?: boolean;
  isTableField?: boolean;
  tableRow?: any;
}

export const defaultSelectDropProps: Partial<SelectDropProps> = {
  name: "",
  code: "",
  showName: "",
  dataBinding: "",
  alignment: AlignmentTypeEnum.Left,
  prompt: "",
  placeholder: "",
  inputStatus: InputStatusEnum.editable,
  defaultValue: "",
  required: RequiredEnum.notRequired,
  displayed: DisplayedEnum.show,
  extraConfig: {},
  selectType: SelectTypeEnum.single,
  filterType: FilterTypeEnum.NotSupported,
  selectDatasource: null,
  showFieldLabel: true,
  targetValue: "",
  targetProp: "",
  isView: false,
  isTableField: false,
  tableRow: {},
};
