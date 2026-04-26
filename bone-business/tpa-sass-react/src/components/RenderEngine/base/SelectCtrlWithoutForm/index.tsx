import React, { useState, useEffect } from 'react';
import { Cascader } from 'antd';

interface SelectCtrlWithoutFormProps {
  value: any;
  onChange: (value: any) => void;
  field: {
    selectDatasource?: any[];
    selectLevel?: number;
  };
  style?: React.CSSProperties;
}

const SelectCtrlWithoutForm: React.FC<SelectCtrlWithoutFormProps> = ({
  value,
  onChange,
  field,
  style
}) => {
  const [options, setOptions] = useState<any[]>([]);

  useEffect(() => {
    if (field.selectDatasource) {
      setOptions(field.selectDatasource);
    }
  }, [field.selectDatasource]);

  const handleChange = (value: any) => {
    // 处理级联选择值
    onChange(value);
  };

  return (
    <Cascader
      options={options}
      value={value}
      onChange={handleChange}
      style={style}
      allowClear
    />
  );
};

export default SelectCtrlWithoutForm;