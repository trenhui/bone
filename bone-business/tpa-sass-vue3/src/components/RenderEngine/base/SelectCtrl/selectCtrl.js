import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";
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
    selectLevel: {
      type: Number,
      default: SelectLevelEnum.TWO,
    },
    selectDatasource: {
      type: Object,
      default: null,
    },
    isTableField: {
      type: Boolean,
      default: false,
    },
    showFieldLabel: {
      type: Boolean,
      default: true,
    },
    targetValue: {
      type: String,
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
  };
}

export const createEmits = () => {
  return ["change", "blur"];
};
