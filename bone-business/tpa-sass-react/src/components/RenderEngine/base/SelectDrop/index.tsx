
import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Form, Select, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { useScopeData } from "../../hooks/useScopeData";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { SelectDropProps, defaultSelectDropProps } from "./props";
import { getRuleVerifyTypeShortLabel, RuleVerifyTypeEnum } from "@/enums/rule/RuleVerifyTypeEnum";

interface PKSelectDropProps extends SelectDropProps {
  onChange?: (value: any) => void;
  onBlur?: () => void;
}

const PKSelectDrop: React.FC<PKSelectDropProps> = (props) => {
  const mergedProps = { ...defaultSelectDropProps, ...props };
  const [value, setValue] = useState<any>("");
  const scopeData = useScopeData();

  const displayMode = scopeData.getData("displayMode") as DisplayModeEnum;
  const componentManager = scopeData.getData("componentManager");
  const dataManager = scopeData.getData("dataManager");
  const rulesManager = scopeData.getData("rulesManager");

  const {
    currentDisplayed,
    currentInputStatus,
    currentRequired,
  } = useBaseComponentProperty(mergedProps);

  const isRequired = useMemo(() => currentRequired === RequiredEnum.required, [currentRequired]);
  const isEditable = useMemo(() => currentInputStatus === InputStatusEnum.editable, [currentInputStatus]);
  const isDisplayed = useMemo(() => currentDisplayed === DisplayedEnum.show, [currentDisplayed]);
  const isViewing = useMemo(() => displayMode === DisplayModeEnum.VIEW || mergedProps.isView, [displayMode, mergedProps.isView]);
  const isConfig = useMemo(() => displayMode === DisplayModeEnum.CONFIG, [displayMode]);
  const isMultiple = useMemo(() => mergedProps.selectType === SelectTypeEnum.multiple, [mergedProps.selectType]);

  const targetRule = useMemo(() => {
    if (isViewing) return [];
    
    const result: any[] = [];
    
    if (isRequired) {
      result.push({
        required: true,
        message: `【${getRuleVerifyTypeShortLabel(
          RuleVerifyTypeEnum.STRONG_VERIFY
        )}】${mergedProps.showName}：必填项`,
        trigger: "change",
      });
    }
    
    return result;
  }, [isViewing, isRequired, mergedProps.showName]);

  const options = useMemo(() => {
    if (!mergedProps.selectDatasource?.options) return [];
    return mergedProps.selectDatasource.options.map((option: any) => ({
      value: option.value,
      label: option.label,
    }));
  }, [mergedProps.selectDatasource]);

  const initModelValue = useCallback((val: any) => {
    let newValue: any;
    let shouldEmitChange = false;

    if (val === undefined || val === null || val === "" || (Array.isArray(val) && val.length === 0)) {
      if (isRequired && mergedProps.defaultValue) {
        newValue = mergedProps.defaultValue;
        shouldEmitChange = true;
      } else {
        newValue = isMultiple ? [] : "";
      }
    } else {
      newValue = val;
    }

    if (JSON.stringify(value) === JSON.stringify(newValue)) return;

    setValue(newValue);

    if (shouldEmitChange) {
      props.onChange?.(newValue);
    }
  }, [isRequired, mergedProps.defaultValue, value, props.onChange, isMultiple]);

  useEffect(() => {
    initModelValue(mergedProps.targetValue);
  }, [mergedProps.targetValue, initModelValue]);

  const handleChange = useCallback((newValue: any) => {
    setValue(newValue);
    props.onChange?.(newValue);
  }, [props.onChange]);

  const handleBlur = useCallback(() => {
    props.onBlur?.();
  }, [props.onBlur]);

  if (!isDisplayed) return null;

  return (
    <div className="select-drop" onClick={(e) => e.stopPropagation()}>
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
        <Select
          value={value}
          onChange={handleChange}
          placeholder={mergedProps.placeholder}
          disabled={!isEditable || isConfig || isViewing}
          onBlur={handleBlur}
          mode={isMultiple ? "multiple" : undefined}
          options={options}
          allowClear
          showSearch
        />
      </Form.Item>
    </div>
  );
};

export default PKSelectDrop;
