import React, { useState, useEffect } from 'react';
import { Select } from 'antd';

const { Option } = Select;

interface SelectDropWithoutFormProps {
  value: any;
  onChange: (value: any) => void;
  field: {
    selectDatasource?: any[];
    multiple?: boolean;
    filterable?: boolean;
  };
  format?: string;
  style?: React.CSSProperties;
}

const SelectDropWithoutForm: React.FC<SelectDropWithoutFormProps> = ({
  value,
  onChange,
  field,
  format = 'string',
  style
}) => {
  const [options, setOptions] = useState<any[]>([]);

  useEffect(() => {
    if (field.selectDatasource) {
      setOptions(field.selectDatasource);
    }
  }, [field.selectDatasource]);

  return (
    <Select
      value={value}
      onChange={onChange}
      mode={field.multiple ? 'multiple' : undefined}
      filterOption={field.filterable}
      style={style}
      allowClear
    >
      {options.map((option) => (
        <Option key={option.value} value={option.value}>
          {option.label}
        </Option>
      ))}
    </Select>
  );
};

export default SelectDropWithoutForm;