import React, { useState, useEffect } from 'react';
import { Input, InputNumber, DatePicker, Select } from 'antd';
import dayjs from 'dayjs';

// Components
import SelectDropWithoutForm from '../SelectDropWithoutForm';
import SelectCtrlWithoutForm from '../SelectCtrlWithoutForm';

// Enums
import { BaseCompType } from '@/enums/baseComp/BaseCompEnum';
import { DataFormatEnum } from '@/enums/baseComp/DataFormatEnum';
import { SelectTypeEnum } from '@/enums/baseComp/SelectTypeEnum';
import { FilterTypeEnum } from '@/enums/baseComp/FilterTypeEnum';
import { getDateFormat, getDateRangeType, getDateTimeType } from '@/enums/baseComp/DateFormatEnum';

const { RangePicker } = DatePicker;

interface SmartInputProps {
  type: string;
  modelValue: any;
  onChange: (value: any) => void;
  limitedLength?: number;
  decimalDigit?: number;
  min?: number;
  max?: number;
  multiples?: number;
  dateFormatType?: number;
  selectDatasource?: any[];
  selectLevel?: number;
  selectType?: SelectTypeEnum;
  filterType?: FilterTypeEnum;
  earliestDatetimeType?: number;
  earliestDatetime?: string;
  latestDatetimeType?: number;
  latestDatetime?: string;
  dataFormat?: DataFormatEnum;
}

const SmartInput: React.FC<SmartInputProps> = ({
  type,
  modelValue,
  onChange,
  limitedLength,
  decimalDigit,
  min,
  max,
  multiples,
  dateFormatType,
  selectDatasource,
  selectLevel,
  selectType,
  filterType,
  earliestDatetimeType,
  earliestDatetime,
  latestDatetimeType,
  latestDatetime,
  dataFormat
}) => {
  const [internalValue, setInternalValue] = useState(modelValue);

  useEffect(() => {
    setInternalValue(modelValue);
  }, [modelValue]);

  const handleChange = (value: any) => {
    setInternalValue(value);
    onChange(value);
  };

  const normalizedType = type.replace('PK', '');

  const isValidNumber = (value: any) => {
    return value !== null && value !== undefined && !isNaN(Number(value));
  };

  const targetDecimalDigit = isValidNumber(decimalDigit) && decimalDigit >= 0 ? decimalDigit : 0;
  const targetMin = isValidNumber(min) ? Number(min) : null;
  const targetMax = isValidNumber(max) ? Number(max) : null;
  const targetMultiples = isValidNumber(multiples) && multiples > 0 ? Number(multiples) : null;
  const dateFormat = getDateFormat(dateFormatType);
  const dateTimeType = getDateTimeType(dateFormatType);
  const dateRangeType = getDateRangeType(dateFormatType);
  const isMultiple = selectType === SelectTypeEnum.multiple;
  const isFilter = filterType === FilterTypeEnum.Supported;

  const disabledDate = (date: dayjs.Dayjs) => {
    let earliestDate = null;
    if (earliestDatetimeType === 0 && earliestDatetime) {
      earliestDate = dayjs(earliestDatetime).toDate();
    } else if (earliestDatetimeType === 1) {
      earliestDate = dayjs().startOf('day').toDate();
    }

    let latestDate = null;
    if (latestDatetimeType === 0 && latestDatetime) {
      latestDate = dayjs(latestDatetime).toDate();
    } else if (latestDatetimeType === 1) {
      latestDate = dayjs().endOf('day').toDate();
    }

    if (earliestDate && date.isBefore(dayjs(earliestDate))) {
      return true;
    }
    if (latestDate && date.isAfter(dayjs(latestDate))) {
      return true;
    }

    return false;
  };

  const renderComponent = () => {
    switch (normalizedType) {
      case BaseCompType.Input:
        return (
          <Input
            value={internalValue}
            onChange={(e) => handleChange(e.target.value)}
            maxLength={limitedLength}
            allowClear
            style={{ width: '100%' }}
          />
        );
      case BaseCompType.InputNum:
        return (
          <InputNumber
            value={internalValue}
            onChange={handleChange}
            min={targetMin || undefined}
            max={targetMax || undefined}
            step={targetMultiples || undefined}
            stepStrictly={!!targetMultiples}
            precision={targetDecimalDigit}
            style={{ width: '100%' }}
            formatter={(value) => {
              if (dataFormat === DataFormatEnum.percentage) {
                return `${value}%`;
              }
              if (dataFormat === DataFormatEnum.money) {
                return `￥${value}`;
              }
              return value;
            }}
            parser={(value) => {
              if (dataFormat === DataFormatEnum.percentage) {
                return parseFloat(value.replace('%', ''));
              }
              if (dataFormat === DataFormatEnum.money) {
                return parseFloat(value.replace('￥', ''));
              }
              return parseFloat(value);
            }}
          />
        );
      case BaseCompType.DateTime:
        return (
          <DatePicker
            value={internalValue ? dayjs(internalValue, dateFormat) : null}
            onChange={(date) => handleChange(date ? date.format(dateFormat) : null)}
            format={dateFormat}
            disabledDate={disabledDate}
            style={{ width: '100%' }}
            allowClear
          />
        );
      case BaseCompType.DateRange:
        return (
          <RangePicker
            value={internalValue && internalValue.includes(',') 
              ? internalValue.split(',').map(d => dayjs(d.trim(), dateFormat)) 
              : null}
            onChange={(dates) => {
              if (dates) {
                handleChange(dates.map(date => date.format(dateFormat)).join(','));
              } else {
                handleChange(null);
              }
            }}
            format={dateFormat}
            disabledDate={disabledDate}
            style={{ width: '100%' }}
            allowClear
          />
        );
      case BaseCompType.SelectCtrl:
        return (
          <SelectCtrlWithoutForm
            value={internalValue}
            onChange={handleChange}
            field={{
              selectDatasource: selectDatasource,
              selectLevel: selectLevel,
            }}
            style={{ width: '100%' }}
          />
        );
      case BaseCompType.SelectDrop:
        return (
          <SelectDropWithoutForm
            value={internalValue}
            onChange={handleChange}
            field={{
              selectDatasource: selectDatasource,
              multiple: isMultiple,
              filterable: isFilter,
            }}
            format="string"
            style={{ width: '100%' }}
          />
        );
      default:
        return (
          <Input
            value={internalValue}
            onChange={(e) => handleChange(e.target.value)}
            style={{ width: '100%' }}
          />
        );
    }
  };

  return (
    <div className="smart-input" style={{ width: '100%' }}>
      {renderComponent()}
    </div>
  );
};

export default SmartInput;