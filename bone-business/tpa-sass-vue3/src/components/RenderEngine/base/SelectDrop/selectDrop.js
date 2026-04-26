import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { FilterTypeEnum } from "@/enums/baseComp/FilterTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export function createProps() {
  return {
    id: {
      type: String,
      default: "",
      required: true,
    },
    name: {
      type: String,
      default: "",
    },
    code: {
      type: String,
      default: "",
    },
    showName: {
      type: String,
      default: "",
    },
    dataBinding: {
      type: String,
      default: "",
    },
    alignment: {
      type: Number,
      default: AlignmentTypeEnum.Left,
    },
    prompt: {
      type: String,
      default: "",
    },
    placeholder: {
      type: String,
      default: "",
    },
    inputStatus: {
      type: Number,
      default: InputStatusEnum.editable,
    },
    defaultValue: {
      type: String,
      default: "",
    },
    required: {
      type: Number,
      default: RequiredEnum.notRequired,
    },
    displayed: {
      type: Number,
      default: DisplayedEnum.show,
    },
    extraConfig: {
      type: Object,
      default: () => ({}),
    },

    selectType: {
      type: Number,
      default: SelectTypeEnum.single,
    },
    filterType: {
      type: Number,
      default: FilterTypeEnum.NotSupported,
    },
    selectDatasource: {
      type: Object,
      default: null,
    },

    showFieldLabel: {
      type: Boolean,
      default: true,
    },
    targetValue: {
      type: [String, Number, Boolean, Object, Array],
      default: "",
    },
    targetProp: {
      type: String,
      default: "",
    },
    isView: {
      type: Boolean,
      default: false,
    },

    // 用于表格行内的联动
    isTableField: {
      type: Boolean,
      default: false,
    },
    tableRow: {
      type: Object,
      default: () => ({}),
    },
  };
}

export const createEmits = () => {
  return ["change", "blur"];
};
