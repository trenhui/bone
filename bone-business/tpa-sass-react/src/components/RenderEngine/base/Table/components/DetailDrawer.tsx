import React from 'react';
import { Drawer, Form, Button, Space, message } from 'antd';
import { useDataBinding } from '../../../hooks/useDataBinding';
import { useTableSave } from '../../../hooks/useTableSave';

interface TableDetailDrawerProps {
  visible: boolean;
  tableName: string;
  tableIndex: number;
  body: any[];
  tableId: string;
  isCreate: boolean;
  tableRules: any;
  singleEditableColumnList: any[];
  saveInfo: any;
  editable: boolean;
  copyData: any;
  onClose: () => void;
  onUpdate: () => void;
}

const TableDetailDrawer: React.FC<TableDetailDrawerProps> = ({
  visible,
  tableName,
  tableIndex,
  body,
  tableId,
  isCreate,
  tableRules,
  singleEditableColumnList,
  saveInfo,
  editable,
  copyData,
  onClose,
  onUpdate
}) => {
  const [form] = Form.useForm();
  const { save } = useTableSave();

  const handleSave = async () => {
    try {
      await form.validateFields();
      const values = form.getFieldsValue();
      
      // 构建保存数据
      const saveData = isCreate ? copyData : {};
      
      // 处理表单数据
      body.forEach((item) => {
        const { targetProp } = useDataBinding(item.dataBinding, tableIndex);
        if (values[item.id] !== undefined) {
          targetProp.value = values[item.id];
        }
      });

      await save(saveInfo, saveData);
      message.success(isCreate ? '新增成功' : '编辑成功');
      onUpdate();
      onClose();
    } catch (error) {
      console.error('保存失败:', error);
      message.error('保存失败');
    }
  };

  const renderFormItems = () => {
    return body.map((item) => {
      const { targetProp } = useDataBinding(item.dataBinding, tableIndex);
      
      return (
        <Form.Item
          key={item.id}
          label={item.showName}
          name={item.id}
          rules={item.required === 1 ? [{ required: true, message: `请输入${item.showName}` }] : []}
          initialValue={targetProp.value}
        >
          {/* 根据字段类型渲染不同的表单控件 */}
          {renderFormControl(item, targetProp)}
        </Form.Item>
      );
    });
  };

  const renderFormControl = (item: any, targetProp: any) => {
    switch (item.type) {
      case 'Input':
        return <input type="text" className="ant-input" />;
      case 'InputNum':
        return <input type="number" className="ant-input" />;
      case 'SelectCtrl':
        return (
          <select className="ant-select-selector">
            {item.options?.map((option: any) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        );
      default:
        return <input type="text" className="ant-input" />;
    }
  };

  return (
    <Drawer
      title={isCreate ? `新增${tableName}` : `编辑${tableName}`}
      open={visible}
      onClose={onClose}
      width={600}
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSave} disabled={!editable}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        {renderFormItems()}
      </Form>
    </Drawer>
  );
};

export default TableDetailDrawer;