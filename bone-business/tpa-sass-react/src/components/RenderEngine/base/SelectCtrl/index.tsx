import { useState, useMemo, useContext, useEffect, useRef } from "react";
import { Cascader, Form, Tooltip } from "antd";
import { InfoCircleOutlined } from "@ant-design/icons";
import { ScopeContext } from "../../hooks/useScopeData";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { SelectCtrlProps, defaultSelectCtrlProps } from "./props";

const { Item } = Form;

export function SelectCtrl(props: SelectCtrlProps) {
  const {
    id,
    name,
    code,
    showName,
    alignment,
    prompt,
    placeholder,
    inputStatus,
    defaultValue,
    required,
    displayed,
    selectLevel,
    selectDatasource,
    isTableField,
    showFieldLabel,
    targetValue,
    targetProp,
    isView,
    onChange,
    onBlur,
  } = { ...defaultSelectCtrlProps, ...props };

  const scopeContext = useContext(ScopeContext);
  const displayMode = scopeContext?.data?.displayMode;
  const rulesManager = scopeContext?.data?.rulesManager;
  const componentManager = scopeContext?.data?.componentManager;
  const dataManager = scopeContext?.data?.dataManager;

  const cascaderRef = useRef<any>(null);
  const [modelValue, setModelValue] = useState<string[]>([]);
  const [dictOptions, setDictOptions] = useState<any[]>([]);

  const currentDisplayed = displayed;
  const currentInputStatus = inputStatus;
  const currentRequired = required;

  const isRequired = currentRequired === RequiredEnum.required;
  const isEditable = currentInputStatus === InputStatusEnum.editable;
  const isDisplayed = currentDisplayed === DisplayedEnum.show;
  const isViewing = displayMode === DisplayModeEnum.VIEW || isView;
  const isConfig = displayMode === DisplayModeEnum.EDIT;
  const isModeEdit = displayMode === DisplayModeEnum.EDIT;
  const isPreview = displayMode === DisplayModeEnum.PREVIEW;

  const sourceType = selectDatasource?.type;
  const sourceCode = selectDatasource?.code;
  const maxLevel = useMemo(() => {
    return selectLevel === SelectLevelEnum.THREE ? 2 : 1;
  }, [selectLevel]);

  const linkageRuleList = useMemo(() => {
    return rulesManager?.get(1, id) || [];
  }, [rulesManager, id]);

  const fieldTableRuleList = useMemo(() => {
    return rulesManager?.get(3, id) || [];
  }, [rulesManager, id]);

  const processInnerValue = (value: string): string[] => {
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      return parsed?.code || [];
    } catch {
      return [];
    }
  };

  const processToSaveValue = (value: string[]): string => {
    return JSON.stringify({
      code: value,
      desc: [],
    });
  };

  const handleLinkageRules = (value: string) => {
    // 这里需要实现联动规则处理
  };

  const handleFieldTableLinkageRules = (value: string) => {
    // 这里需要实现字段表格联动规则处理
  };

  const loadCascaderData = (selectedOptions: any[]) => {
    // 这里需要实现级联数据加载
    return new Promise<void>((resolve) => {
      setTimeout(() => {
        resolve();
      }, 500);
    });
  };

  const initModelValue = (val: any) => {
    let actualValue;
    let shouldEmitChange = false;

    const processedVal = processInnerValue(val);
    const isEmpty =
      val === undefined ||
      val === null ||
      val === "" ||
      processedVal.length === 0 ||
      processedVal.every((item) => item == "");

    if (isEmpty) {
      if (isRequired && defaultValue) {
        actualValue = defaultValue;
        shouldEmitChange = true;
      } else {
        actualValue = null;
      }
    } else {
      actualValue = val;
    }

    const value = processInnerValue(actualValue);

    if (JSON.stringify(modelValue) === JSON.stringify(value)) return;

    setModelValue(value);

    if (shouldEmitChange) {
      onChange?.(actualValue);
    }

    handleLinkageRules(processToSaveValue(value));
    handleFieldTableLinkageRules(processToSaveValue(value));
  };

  useEffect(() => {
    initModelValue(targetValue);
  }, [targetValue]);

  const handleChange = (newVal: string[]) => {
    setModelValue(newVal);
    const saveValue = processToSaveValue(newVal);
    onChange?.(saveValue);
    handleLinkageRules(saveValue);
    handleFieldTableLinkageRules(saveValue);
  };

  const handleVisibleChange = (visible: boolean) => {
    if (!visible) {
      onBlur?.();
    }
  };

  const cascaderProps = isConfig
    ? undefined
    : {
        value: "code",
        label: "name",
        children: "children",
        checkStrictly: true,
        lazy: true,
        loadCascaderData,
      };

  if (!isDisplayed) return null;

  return (
    <div className="select-ctrl" onClick={(e) => e.stopPropagation()}>
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
                        )}】${showName}：必填项`,
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
        <Cascader
          ref={cascaderRef}
          value={modelValue}
          options={dictOptions}
          onChange={handleChange}
          style={{ width: "100%" }}
          placeholder={placeholder}
          disabled={!isEditable || isConfig || isViewing}
          onVisibleChange={handleVisibleChange}
          showSearch
          clearable
          {...cascaderProps}
        />
      </Item>
    </div>
  );
}

export default SelectCtrl;
