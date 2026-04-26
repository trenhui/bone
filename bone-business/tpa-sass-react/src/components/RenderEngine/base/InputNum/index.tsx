import { useState, useMemo, useContext, useEffect } from "react";
import { InputNumber, Form, Tooltip } from "antd";
import { InfoCircleOutlined } from "@ant-design/icons";
import { ScopeContext } from "../../hooks/useScopeData";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputNumProps, defaultInputNumProps } from "./props";

const { Item } = Form;

export function InputNum(props: InputNumProps) {
  const {
    id,
    name,
    code,
    showName,
    prompt,
    placeholder,
    alignment,
    dataFormat,
    min,
    max,
    decimalDigit,
    multiples,
    inputStatus,
    required,
    displayed,
    defaultValue,
    isTableField,
    showFieldLabel,
    targetValue,
    targetProp,
    isView,
    onChange,
    onBlur,
  } = { ...defaultInputNumProps, ...props };

  const scopeContext = useContext(ScopeContext);
  const displayMode = scopeContext?.data?.displayMode;
  const rulesManager = scopeContext?.data?.rulesManager;
  const componentManager = scopeContext?.data?.componentManager;
  const dataManager = scopeContext?.data?.dataManager;

  const [modelValue, setModelValue] = useState<number | undefined>(undefined);

  const currentDisplayed = displayed;
  const currentInputStatus = inputStatus;
  const currentRequired = required;

  const isRequired = currentRequired === RequiredEnum.required;
  const isEditable = currentInputStatus === InputStatusEnum.editable;
  const isDisplayed = currentDisplayed === DisplayedEnum.show;
  const isViewing = displayMode === DisplayModeEnum.VIEW || isView;
  const isConfig = displayMode === DisplayModeEnum.EDIT;

  const isValidNumber = (value: any) =>
    value !== null && value !== undefined && !isNaN(Number(value));

  const targetDecimalDigit = useMemo(() => {
    return isValidNumber(decimalDigit) && decimalDigit >= 0 ? decimalDigit : 0;
  }, [decimalDigit]);

  const targetMin = useMemo(() => {
    return isValidNumber(min) ? Number(min) : null;
  }, [min]);

  const targetMax = useMemo(() => {
    return isValidNumber(max) ? Number(max) : null;
  }, [max]);

  const targetMultiples = useMemo(() => {
    return isValidNumber(multiples) && multiples > 0 ? Number(multiples) : null;
  }, [multiples]);

  const linkageRuleList = useMemo(() => {
    return rulesManager?.get(1, id) || [];
  }, [rulesManager, id]);

  const fieldTableRuleList = useMemo(() => {
    return rulesManager?.get(3, id) || [];
  }, [rulesManager, id]);

  const handleLinkageRules = (value: number | string | undefined) => {
    // 这里需要实现联动规则处理
  };

  const handleFieldTableLinkageRules = (value: number | string | undefined) => {
    // 这里需要实现字段表格联动规则处理
  };

  const initModelValue = (val: any) => {
    if (!isValidNumber(val)) {
      val = undefined;
    }
    if (typeof val === "string") {
      val = Number(val);
    }

    let newValue;
    let shouldEmitChange = false;

    if (val === undefined || val === null) {
      if (isRequired && isValidNumber(defaultValue)) {
        newValue = Number(defaultValue);
        shouldEmitChange = true;
      } else {
        newValue = undefined;
      }
    } else {
      newValue = val;
    }

    if (modelValue === newValue) return;

    setModelValue(newValue);

    if (shouldEmitChange) {
      onChange?.(newValue);
    }

    handleLinkageRules(modelValue);
    handleFieldTableLinkageRules(modelValue);
  };

  useEffect(() => {
    initModelValue(targetValue);
  }, [targetValue]);

  const handleChange = (value: number | null) => {
    const newVal = value === null ? undefined : value;
    setModelValue(newVal);
    onChange?.(newVal);
  };

  const handleBlur = () => {
    onBlur?.();
    handleLinkageRules(modelValue);
    handleFieldTableLinkageRules(modelValue);
  };

  if (!isDisplayed) return null;

  const suffixText =
    dataFormat === DataFormatEnum.percentage ? "%" : dataFormat === DataFormatEnum.money ? "￥" : null;

  return (
    <div className="input-num" onClick={(e) => e.stopPropagation()}>
      <div className="input-num-content">
        <Item
          required={isRequired}
          name={targetProp}
          rules={
            isViewing
              ? []
              : [
                  ...(isRequired
                    ? [
                        {
                          required: true,
                          message: `【${getRuleVerifyTypeShortLabel(
                            RuleVerifyTypeEnum.STRONG_VERIFY
                          )}】${showName}：请输入有效数字`,
                          trigger: "blur",
                        },
                      ]
                    : []),
                ]
          }
          label={
            showFieldLabel ? (
              <>
                <span>{showName}</span>
                {prompt && (
                  <span className="ml-1">
                    <Tooltip title={prompt} placement="top">
                      <InfoCircleOutlined style={{ fontSize: "1em" }} />
                    </Tooltip>
                  </span>
                )}
              </>
            ) : null
          }
        >
          <InputNumber
            value={modelValue}
            onChange={handleChange}
            onBlur={handleBlur}
            placeholder={placeholder}
            disabled={!isEditable || isConfig || isViewing}
            style={{ width: "100%" }}
            controls={false}
            min={targetMin ?? undefined}
            max={targetMax ?? undefined}
            step={targetMultiples ?? undefined}
            precision={targetDecimalDigit ?? undefined}
            addonAfter={suffixText}
          />
        </Item>
      </div>
    </div>
  );
}

export default InputNum;
