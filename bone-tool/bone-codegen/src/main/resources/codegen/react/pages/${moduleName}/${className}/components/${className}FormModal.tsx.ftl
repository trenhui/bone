/**
 * ${className} 表单弹窗组件
 * 复用统一弹窗编辑表单
 */
import React from 'react';
import { Modal, Form, Input, DatePicker, Switch, Select } from 'antd';
import { ${className}FormModalProps } from '../../../${lowerClassName}/types';
import { ${className} } from '../../../../services/${moduleName}/${lowerClassName}Api';
import { formatDateTime } from '@/utils/format';

/**
 * ${className} 表单弹窗组件
 */
const ${className}FormModal: React.FC<${className}FormModalProps> = ({
  open,
  currentRecord,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm();

  React.useEffect(() => {
    if (open) {
      form.resetFields();
      if (currentRecord) {
        form.setFieldsValue(currentRecord);
      }
    }
  }, [open, currentRecord, form]);

  /**
   * 处理表单提交
   */
  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      await onSubmit(values);
      form.resetFields();
    } catch (error) {
      console.error('表单验证失败:', error);
    }
  };

  return (
    <Modal
      title={currentRecord ? '编辑${className}' : '新增${className}'}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      destroyOnClose
      width={600}
    >
      <Form form={form} layout="vertical" initialValues={currentRecord || {}}>
<#list columns as column>
  <#if !column.primaryKey>
        <Form.Item
          label="${column.columnComment}"
          name="${column.javaField}"
<#if column.nullable?? && !column.nullable>
          rules={[{ required: true, message: '请输入${column.columnComment}' }]}
</#if>
        >
<#if column.javaType == "LocalDateTime" || column.javaType == "Date">
          <DatePicker showTime style={{ width: '100%' }} />
<#elseif column.javaType == "Boolean">
          <Switch />
<#elseif column.dictType?? && column.dictType?hasContent>
          <Select placeholder="请选择${column.columnComment}">
            {/* 字典选项会动态加载 */}
          </Select>
<#else>
          <Input placeholder="请输入${column.columnComment}" />
</#if>
        </Form.Item>
  </#if>
</#list>
      </Form>
    </Modal>
  );
};

export default ${className}FormModal;
