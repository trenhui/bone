import { useState, useMemo, useContext, useEffect } from "react";
import { DatePicker, Form, Tooltip } from "antd";
import { InfoCircleOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { ScopeContext } from "../../hooks/useScopeData";
import {
  getDateFormat,
  getDateTimeType,
} from "@/enums/baseComp/DateFormatEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { DateTimeProps, defaultDateTimeProps } from "./props";

const { Item } = Form;
const { DatePicker: AntDatePicker } = DatePicker;

export function DateTime(props: DateTimeProps) {
  const {
    id,
    name,
    code,
    showName,
    prompt,
    placeholder,
    alignment,
    dateFormatType,
    earliestDatetime,
    earliestDatetimeType,
    latestDatetime,
    latestDatetimeType,
    defaultValue,
    inputStatus,
    required,
    displayed,
    isTableField,
    showFieldLabel,
    targetValue,
    targetProp,
    isView,
    onChange,
    onBlur,
  } = { ...defaultDateTimeProps, ...props };

  const scopeContext = useContext(ScopeContext);
  const displayMode = scopeContext?.data?.displayMode;
  const rulesManager = scopeContext?.data?.rulesManager;
  const componentManager = scopeContext?.data?.componentManager;
  const dataManager = scopeContext?.data?.dataManager;

  const [modelValue, setModelValue] = useState<string>("");

  const currentDisplayed = displayed;
  const currentInputStatus = inputStatus;
  const currentRequired = required;

  const isRequired = currentRequired === RequiredEnum.required;
  const isEditable = currentInputStatus === InputStatusEnum.editable;
  const isDisplayed = currentDisplayed === DisplayedEnum.show;
  const isViewing = displayMode === DisplayModeEnum.VIEW || isView;
  const isConfig = displayMode === DisplayModeEnum.EDIT;

  const dateFormat = useMemo(() => getDateFormat(dateFormatType), [dateFormatType]);
  const datePickerType = useMemo(() => getDateTimeType(dateFormatType), [dateFormatType]);

  const linkageRuleList = useMemo(() => {
    return rulesManager?.get(1, id) || [];
  }, [rulesManager, id]);

  const fieldTableRuleList = useMemo(() => {
    return rulesManager?.get(3, id) || [];
  }, [rulesManager, id]);

  const disabledDate = (current: dayjs.Dayjs | null) => {
    if (!current) return false;

    let earliestDate = null;
    if (earliestDatetimeType === 0 && earliestDatetime) {
      earliestDate = dayjs(earliestDatetime).toDate();
    } else if (earliestDatetimeType === 1) {
      earliestDate = dayjs().startOf("day").toDate();
    }

    let latestDate = null;
    if (latestDatetimeType === 0 && latestDatetime) {
      latestDate = dayjs(latestDatetime).toDate();
    } else if (latestDatetimeType === 1) {
      latestDate = dayjs().endOf("day").toDate();
    }

    const currentDate = current.toDate();
    if (earliestDate && currentDate < earliestDate) {
      return true;
    }
    if (latestDate && currentDate > latestDate) {
      return true;
    }

    return false;
  };

  const handleLinkageRules = (value: string) => {
    // 这里需要实现联动规则处理
  };

  const handleFieldTableLinkageRules = (value: string) => {
    // 这里需要实现字段表格联动规则处理
  };

  const initModelValue = (val: any) => {
    let actualValue;
    let shouldEmitChange = false;

    if (val === undefined || val === null || val === "") {
      if (isRequired && defaultValue && dayjs(defaultValue).isValid()) {
        actualValue = defaultValue;
        shouldEmitChange = true;
      } else {
        actualValue = "";
      }
    } else {
      actualValue = val;
    }

    if (dayjs(actualValue).isValid()) {
      const value = dayjs(actualValue).format(dateFormat);
      if (modelValue === value) return;

      setModelValue(value);

      if (shouldEmitChange) {
        onChange?.(value);
      }

      handleLinkageRules(value);
      handleFieldTableLinkageRules(value);
    } else if (!actualValue) {
      if (modelValue === "") return;

      setModelValue("");
      handleLinkageRules("");
      handleFieldTableLinkageRules("");
    }
  };

  useEffect(() => {
    initModelValue(targetValue);
  }, [targetValue]);

  const handleChange = (date: dayjs.Dayjs | null) => {
    const newVal = date ? date.format(dateFormat) : "";
    setModelValue(newVal);
    onChange?.(newVal);
  };

  const handleBlur = () => {
    onBlur?.();
    handleLinkageRules(modelValue);
    handleFieldTableLinkageRules(modelValue);
  };

  if (!isDisplayed) return null;

  return (
    <div className="date-picker" onClick={(e) => e.stopPropagation()}>
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
        <AntDatePicker
          value={modelValue ? dayjs(modelValue, dateFormat) : null}
          picker={datePickerType as any}
          format={dateFormat}
          disabled={!isEditable || isConfig || isViewing}
          onChange={handleChange}
          disabledDate={disabledDate}
          placeholder={placeholder}
          style={{ width: "100%" }}
          onBlur={handleBlur}
          allowClear
        />
      </Item>
    </div>
  );
}

export default DateTime;
