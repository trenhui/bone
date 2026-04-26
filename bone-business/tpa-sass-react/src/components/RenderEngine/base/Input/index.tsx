
import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Form, Input, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { useScopeData } from "../../hooks/useScopeData";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { InputValueTypeEnum } from "@/enums/baseComp/InputValueTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { InputProps, defaultInputProps } from "./props";
import { validateIdCard, validatePhone, validateContact } from "@/utils/strUtils";
import { getRuleVerifyTypeShortLabel, RuleVerifyTypeEnum } from "@/enums/rule/RuleVerifyTypeEnum";

interface PKInputProps extends InputProps {
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

const PKInput: React.FC<PKInputProps> = (props) => {
  const mergedProps = { ...defaultInputProps, ...props };
  const [value, setValue] = useState<string>("");
  const scopeData = useScopeData();

  const displayMode = scopeData.getData("displayMode") as DisplayModeEnum;
  const componentManager = scopeData.getData("componentManager");
  const dataManager = scopeData.getData("dataManager");
  const rulesManager = scopeData.getData("rulesManager");
  const updateSchema = scopeData.getData("updateSchema");

  const {
    currentDisplayed,
    currentInputStatus,
    currentRequired,
    currentValueType,
  } = useBaseComponentProperty(mergedProps);

  const isRequired = useMemo(() => currentRequired === RequiredEnum.required, [currentRequired]);
  const isEditable = useMemo(() => currentInputStatus === InputStatusEnum.editable, [currentInputStatus]);
  const isDisplayed = useMemo(() => currentDisplayed === DisplayedEnum.show, [currentDisplayed]);
  const isViewing = useMemo(() => displayMode === DisplayModeEnum.VIEW || mergedProps.isView, [displayMode, mergedProps.isView]);
  const isConfig = useMemo(() => displayMode === DisplayModeEnum.CONFIG, [displayMode]);

  const linkageRuleList = useMemo(() => rulesManager?.get(1, mergedProps.id) || [], [rulesManager, mergedProps.id]);
  const fieldTableRuleList = useMemo(() => rulesManager?.get(3, mergedProps.id) || [], [rulesManager, mergedProps.id]);

  const targetRule = useMemo(() => {
    if (isViewing) return [];
    
    const result: any[] = [];
    
    if (isRequired) {
      result.push({
        required: true,
        message: `【${getRuleVerifyTypeShortLabel(
          RuleVerifyTypeEnum.STRONG_VERIFY
        )}】${mergedProps.showName}：必填项`,
        trigger: "blur",
      });
    }
    
    if (currentValueType === InputValueTypeEnum.ID_CARD) {
      result.push({
        validator: (_: any, val: string) => {
          if (val && !validateIdCard(val)) {
            return Promise.reject(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${mergedProps.showName}：身份证号校验不正确`
              )
            );
          }
          return Promise.resolve();
        },
        trigger: "blur",
      });
    }
    
    if (currentValueType === InputValueTypeEnum.PHONE) {
      result.push({
        validator: (_: any, val: string) => {
          if (val && !validatePhone(val)) {
            return Promise.reject(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${mergedProps.showName}：手机号校验不正确`
              )
            );
          }
          return Promise.resolve();
        },
        trigger: "blur",
      });
    }
    
    if (currentValueType === InputValueTypeEnum.CONTACT) {
      result.push({
        validator: (_: any, val: string) => {
          if (val && !validateContact(val)) {
            return Promise.reject(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${mergedProps.showName}：联系方式校验不正确`
              )
            );
          }
          return Promise.resolve();
        },
        trigger: "blur",
      });
    }
    
    if (mergedProps.limitedLength && !isNaN(mergedProps.limitedLength)) {
      result.push({
        validator: (_: any, val: string) => {
          if (val !== null && val !== undefined && val !== "" && String(val).length > mergedProps.limitedLength) {
            return Promise.reject(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${mergedProps.showName}：输入长度不能超过${mergedProps.limitedLength}`
              )
            );
          }
          return Promise.resolve();
        },
        trigger: "blur",
      });
    }
    
    return result;
  }, [
    isViewing,
    isRequired,
    currentValueType,
    mergedProps.showName,
    mergedProps.limitedLength,
  ]);

  const initModelValue = useCallback((val: any) => {
    let newValue: string;
    let shouldEmitChange = false;

    if (val === undefined || val === null || val === "") {
      if (isRequired && mergedProps.defaultValue) {
        newValue = mergedProps.defaultValue;
        shouldEmitChange = true;
      } else {
        newValue = "";
      }
    } else {
      newValue = val;
    }

    if (value === newValue) return;

    setValue(newValue);

    if (shouldEmitChange) {
      props.onChange?.(newValue);
    }

    handleLinkageRules(newValue);
    handleFieldTableLinkageRules(newValue);
  }, [isRequired, mergedProps.defaultValue, value, props.onChange]);

  useEffect(() => {
    initModelValue(mergedProps.targetValue);
  }, [mergedProps.targetValue, initModelValue]);

  const handleLinkageRules = useCallback((val: string) => {
    if (linkageRuleList && linkageRuleList.length > 0) {
      for (const rule of linkageRuleList) {
        console.log("Linkage rule execution would happen here", rule, val);
      }
    }
  }, [linkageRuleList]);

  const handleFieldTableLinkageRules = useCallback((val: string) => {
    if (fieldTableRuleList && fieldTableRuleList.length > 0) {
      for (const rule of fieldTableRuleList) {
        console.log("Field table linkage rule execution would happen here", rule, val);
      }
    }
  }, [fieldTableRuleList]);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setValue(newValue);
    props.onChange?.(newValue);
  }, [props.onChange]);

  const handleBlur = useCallback(() => {
    props.onBlur?.();
    handleLinkageRules(value);
    handleFieldTableLinkageRules(value);
  }, [props.onBlur, handleLinkageRules, handleFieldTableLinkageRules, value]);

  if (!isDisplayed) return null;

  return (
    <div className="input-text" onClick={(e) => e.stopPropagation()}>
      <Form.Item
        required={isRequired}
        name={mergedProps.targetProp}
        rules={targetRule}
        label={
          mergedProps.showFieldLabel ? (
            <span>
              {mergedProps.showName}
              {mergedProps.prompt && (
                <span className="ml-1">
                  <Tooltip title={mergedProps.prompt} placement="top">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              )}
            </span>
          ) : null
        }
      >
        <Input
          value={value}
          onChange={handleChange}
          placeholder={mergedProps.placeholder}
          maxLength={mergedProps.limitedLength}
          disabled={!isEditable || isConfig || isViewing}
          onBlur={handleBlur}
          allowClear
        />
      </Form.Item>
    </div>
  );
};

export default PKInput;
