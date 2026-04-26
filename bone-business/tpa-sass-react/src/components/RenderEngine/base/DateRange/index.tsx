import { useState, useMemo, useContext, useEffect } from "react";
import { DatePicker, Form, Tooltip } from "antd";
import { InfoCircleOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { ScopeContext } from "../../hooks/useScopeData";
import {
  getDateFormat,
  getDateRangeType,
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
import { DateRangeValueTypeEnum } from "@/enums/baseComp/DateRangeValueTypeEnum";
import { DateRangeProps, defaultDateRangeProps } from "./props";

const { Item } = Form;
const { RangePicker } = DatePicker;

export function DateRange(props: DateRangeProps) {
  const {
    id,
    name,
    code,
    showName,
    prompt,
    placeholderTwo,
    placeholderThree,
    alignment,
    dateFormatType,
    earliestDatetimeType,
    latestDatetimeType,
    earliestDatetime,
    latestDatetime,
    defaultValue,
    valueType,
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
  } = { ...defaultDateRangeProps, ...props };

  const scopeContext = useContext(ScopeContext);
  const displayMode = scopeContext?.data?.displayMode;
  const rulesManager = scopeContext?.data?.rulesManager;
  const componentManager = scopeContext?.data?.componentManager;
  const dataManager = scopeContext?.data?.dataManager;

  const [modelValue, setModelValue] = useState<string[]>([]);
  const [currentValueType, setCurrentValueType] = useState(valueType);

  const currentDisplayed = displayed;
  const currentInputStatus = inputStatus;
  const currentRequired = required;

  const isRequired = currentRequired === RequiredEnum.required;
  const isEditable = currentInputStatus === InputStatusEnum.editable;
  const isDisplayed = currentDisplayed === DisplayedEnum.show;
  const isViewing = displayMode === DisplayModeEnum.VIEW || isView;
  const isConfig = displayMode === DisplayModeEnum.EDIT;

  const dateFormat = useMemo(() => getDateFormat(dateFormatType), [dateFormatType]);
  const datePickerType = useMemo(() => getDateRangeType(dateFormatType), [dateFormatType]);

  const linkageRuleList = useMemo(() => {
    return rulesManager?.get(1, id) || [];
  }, [rulesManager, id]);

  const fieldTableRuleList = useMemo(() => {
    return rulesManager?.get(3, id) || [];
  }, [rulesManager, id]);

  const formatDate = (date: any, format: string): string => {
    return dayjs(date).isValid() ? dayjs(date).format(format) : "";
  };

  const formatDateRange = (value: any): string[] | string => {
    if (value === null || value === undefined || value === "") {
      return "";
    }
    if (typeof value === "string" && value.includes(",")) {
      const [startDate, endDate] = value.split(",").map((date) => date.trim());
      const formattedStartDate = formatDate(startDate, dateFormat);
      const formattedEndDate = formatDate(endDate, dateFormat);
      return [formattedStartDate, formattedEndDate];
    }
    return formatDate(value, dateFormat);
  };

  const formatOutputValue = (value: any): string => {
    if (value == null || value === "") return "";

    if (Array.isArray(value)) {
      const [start, end] = value;
      return start && end ? `${start},${end}` : "";
    }

    return value;
  };

  const disabledDate = (current: dayjs.Dayjs | null): boolean => {
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

  const handleLinkageRules = (value: any) => {
    // 这里需要实现联动规则处理
  };

  const handleFieldTableLinkageRules = (value: any) => {
    // 这里需要实现字段表格联动规则处理
  };

  const initModelValue = (val: any) => {
    let actualValue;
    let shouldEmitChange = false;

    if (val === undefined || val === null || val === "," || val === "") {
      if (isRequired && defaultValue) {
        actualValue = defaultValue;
        shouldEmitChange = true;
      } else {
        actualValue = "";
      }
    } else {
      actualValue = val;
    }

    const value = formatDateRange(actualValue);
    if (JSON.stringify(modelValue) === JSON.stringify(value)) return;

    setModelValue(Array.isArray(value) ? value : []);

    if (shouldEmitChange) {
      onChange?.(formatOutputValue(value));
    }

    handleLinkageRules(value);
    handleFieldTableLinkageRules(value);
  };

  useEffect(() => {
    initModelValue(targetValue);
  }, [targetValue]);

  const handleChange = (dates: any) => {
    const newVal = dates ? dates.map((date: any) => date.format(dateFormat)) : [];
    setModelValue(newVal);
    onChange?.(formatOutputValue(newVal));
  };

  const handleBlur = () => {
    onBlur?.();
    handleLinkageRules(modelValue);
    handleFieldTableLinkageRules(modelValue);
  };

  const shortcuts = useMemo(() => {
    return currentValueType === DateRangeValueTypeEnum.ID_CARD
      ? [
          {
            text: "长期",
            value: () => [modelValue[0], "9999-12-31"],
          },
          {
            text: "5年",
            value: () => [
              modelValue[0],
              dayjs(modelValue[0]).add(5, "year").format(dateFormat),
            ],
          },
          {
            text: "10年",
            value: () => [
              modelValue[0],
              dayjs(modelValue[0]).add(10, "year").format(dateFormat),
            ],
          },
          {
            text: "20年",
            value: () => [
              modelValue[0],
              dayjs(modelValue[0]).add(20, "year").format(dateFormat),
            ],
          },
        ]
      : undefined;
  }, [currentValueType, modelValue, dateFormat]);

  if (!isDisplayed) return null;

  const pickerValue =
    modelValue.length === 2
      ? [dayjs(modelValue[0], dateFormat), dayjs(modelValue[1], dateFormat)]
      : null;

  return (
    <div className="date-range" onClick={(e) => e.stopPropagation()}>
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
        <RangePicker
          value={pickerValue}
          picker={datePickerType as any}
          format={dateFormat}
          disabled={!isEditable || isConfig || isViewing}
          onChange={handleChange}
          disabledDate={disabledDate}
          placeholder={[placeholderTwo, placeholderThree]}
          separator="至"
          style={{ width: "100%" }}
          onBlur={handleBlur}
          allowClear
          presets={shortcuts}
        />
      </Item>
    </div>
  );
}

export default DateRange;
