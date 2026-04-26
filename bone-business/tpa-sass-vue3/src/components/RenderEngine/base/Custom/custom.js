import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
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
    required: {
      type: Number,
      default: RequiredEnum.notRequired,
    },
    displayed: {
      type: Number,
      default: DisplayedEnum.show,
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
    showFieldLabel: {
      type: Boolean,
      default: true,
    },
    targetValue: {
      type: [String, Array],
      default: "",
    },
    targetProp: {
      type: String,
      default: "",
    },
    isTableField: {
      type: Boolean,
      default: false,
    },
    isView: {
      type: Boolean,
      default: false,
    },

    isCellView: {
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
