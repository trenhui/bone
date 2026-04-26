import React from 'react';
import { Input, Select, DatePicker, InputNumber, Tooltip } from 'antd';
import { useDataBinding } from '../../../hooks/useDataBinding';
import { useBaseComponentProperty } from '../../../hooks/useBaseComponentProperty';

const { Option } = Select;
const { RangePicker } = DatePicker;

interface TableEditableCellProps {
  tableItem: any;
  tableIndex: number;
  tableRowId: string;
  tableId: string;
  isEditing: boolean;
}

const TableEditableCell: React.FC<TableEditableCellProps> = ({ 
  tableItem, 
  tableIndex, 
  tableRowId, 
  tableId, 
  isEditing 
}) => {
  const { targetProp, setValue } = useDataBinding(tableItem.dataBinding, tableIndex);
  const { currentDisplay } = useBaseComponentProperty({ display: tableItem.display });

  if (currentDisplay !== 1) {
    return null;
  }

  const renderContent = () => {
    if (!isEditing) {
      return (
        <div style={{ textAlign: 'center' }}>
          {targetProp.value || '-'}
        </div>
      );
    }

    switch (tableItem.type) {
      case 'Input':
        return (
          <Input
            value={targetProp.value || ''}
            onChange={(e) => setValue(e.target.value)}
            style={{ width: '100%' }}
          />
        );
      case 'InputNum':
        return (
          <InputNumber
            value={targetProp.value}
            onChange={(value) => setValue(value)}
            style={{ width: '100%' }}
          />
        );
      case 'SelectCtrl':
        return (
          <Select
            value={targetProp.value}
            onChange={(value) => setValue(value)}
            style={{ width: '100%' }}
          >
            {tableItem.options?.map((option: any) => (
              <Option key={option.value} value={option.value}>
                {option.label}
              </Option>
            ))}
          </Select>
        );
      case 'DateRange':
        return (
          <RangePicker
            value={targetProp.value ? targetProp.value.map((date: string) => new Date(date)) : null}
            onChange={(dates) => {
              if (dates) {
                setValue(dates.map(date => date.toISOString()));
              } else {
                setValue(null);
              }
            }}
            style={{ width: '100%' }}
          />
        );
      case 'DateTime':
        return (
          <DatePicker
            value={targetProp.value ? new Date(targetProp.value) : null}
            onChange={(date) => {
              if (date) {
                setValue(date.toISOString());
              } else {
                setValue(null);
              }
            }}
            style={{ width: '100%' }}
          />
        );
      default:
        return (
          <Input
            value={targetProp.value || ''}
            onChange={(e) => setValue(e.target.value)}
            style={{ width: '100%' }}
          />
        );
    }
  };

  return (
    <div>
      {renderContent()}
    </div>
  );
};

export default TableEditableCell;