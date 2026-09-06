import React, { useCallback, useEffect, useState } from 'react';
import {
  Card,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
  Space,
  Tag,
  Descriptions,
  InputNumber
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  MasterDataField,
  MasterDataEntity,
  CreateMasterDataFieldReq,
} from '../types';
import { masterDataFieldApi, masterDataEntityApi } from '../services/api';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

const FieldManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [currentField, setCurrentField] = useState<MasterDataField | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [fields, setFields] = useState<MasterDataField[]>([]);
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<number | null>(null);

  const fetchEntities = useCallback(async () => {
    try {
      const response = await masterDataEntityApi.page({ pageSize: 100 });
      if (response.code === 200) {
        setEntities(response.data.list);
        if (response.data.list.length > 0 && !selectedEntityId) {
          setSelectedEntityId(response.data.list[0].id);
        }
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取实体列表失败');
    }
  }, [selectedEntityId]);

  const fetchFields = useCallback(async (entityId: number) => {
    setLoading(true);
    try {
      const response = await masterDataFieldApi.listByEntityId(entityId);
      if (response.code === 200) {
        setFields(response.data);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取字段列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchEntities();
  }, [fetchEntities]);

  useEffect(() => {
    if (selectedEntityId) {
      void fetchFields(selectedEntityId);
    }
  }, [selectedEntityId, fetchFields]);

  // 打开创建模态框
  const handleAdd = () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个实体');
      return;
    }
    setIsEditMode(false);
    setCurrentField(null);
    form.resetFields();
    form.setFieldsValue({ masterDataEntityId: selectedEntityId });
    setIsModalOpen(true);
  };

  // 打开编辑模态框
  const handleEdit = (record: MasterDataField) => {
    setIsEditMode(true);
    setCurrentField(record);
    form.setFieldsValue({
      name: record.name,
      type: record.type,
      length: record.length,
      required: record.required,
      defaultValue: record.defaultValue,
      description: record.description
    });
    setIsModalOpen(true);
  };

  // 打开查看模态框
  const handleView = (record: MasterDataField) => {
    setCurrentField(record);
    setIsViewModalOpen(true);
  };

  // 删除字段
  const handleDelete = async (id: number) => {
    try {
      const response = await masterDataFieldApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        if (selectedEntityId) {
          fetchFields(selectedEntityId);
        }
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      let response;
      if (isEditMode && currentField) {
        response = await masterDataFieldApi.update(currentField.id, values);
      } else {
        response = await masterDataFieldApi.create(values as CreateMasterDataFieldReq);
      }
      if (response.code === 200) {
        message.success(isEditMode ? '更新成功' : '创建成功');
        setIsModalOpen(false);
        if (selectedEntityId) {
          fetchFields(selectedEntityId);
        }
      } else {
        message.error(response.message);
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 字段类型选项
  const fieldTypes = [
    { value: 'STRING', label: '字符串' },
    { value: 'NUMBER', label: '数字' },
    { value: 'DATE', label: '日期' },
    { value: 'BOOLEAN', label: '布尔值' },
    { value: 'TEXT', label: '文本' }
  ];

  // 表格列定义
  const columns = [
    {
      title: '字段名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: MasterDataField) => (
        <a onClick={() => handleView(record)}>{text}</a>
      )
    },
    {
      title: '字段类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const typeMap = {
          STRING: '字符串',
          NUMBER: '数字',
          DATE: '日期',
          BOOLEAN: '布尔值',
          TEXT: '文本'
        };
        return typeMap[type as keyof typeof typeMap] || type;
      }
    },
    {
      title: '长度',
      dataIndex: 'length',
      key: 'length',
      render: (length: number) => length || '-'
    },
    {
      title: '是否必填',
      dataIndex: 'required',
      key: 'required',
      render: (required: boolean) => (
        <Tag color={required ? 'red' : 'green'}>{required ? '是' : '否'}</Tag>
      )
    },
    {
      title: '默认值',
      dataIndex: 'defaultValue',
      key: 'defaultValue',
      render: (defaultValue: string) => defaultValue || '-'
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: MasterDataField) => (
        <Space size="middle">
          <Button
            type="primary"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: '20px' }}>
      <Card title="主数据字段管理">
        {/* 实体选择 */}
        <Form layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item label="选择实体">
            <Select
              style={{ width: 300 }}
              placeholder="请选择主数据实体"
              value={selectedEntityId}
              onChange={setSelectedEntityId}
            >
              {entities.map(entity => (
                <Option key={entity.id} value={entity.id}>
                  {entity.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              创建字段
            </Button>
          </Form.Item>
        </Form>

        {/* 字段列表 */}
        <ProTable
          options={false}
          columns={columns as ProColumns<MasterDataField>[]}
          dataSource={fields}
          loading={loading}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: '请先选择一个实体' }}
        />
      </Card>

      {/* 创建/编辑模态框 */}
      <Modal
        title={isEditMode ? '编辑字段' : '创建字段'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="masterDataEntityId"
            label="所属实体"
            rules={[{ required: true, message: '请选择所属实体' }]}
          >
            <Select disabled>
              {entities.map(entity => (
                <Option key={entity.id} value={entity.id}>
                  {entity.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="name"
            label="字段名称"
            rules={[{ required: true, message: '请输入字段名称' }]}
          >
            <Input placeholder="请输入字段名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="字段类型"
            rules={[{ required: true, message: '请选择字段类型' }]}
          >
            <Select placeholder="请选择字段类型">
              {fieldTypes.map(type => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="length" label="长度">
            <InputNumber placeholder="请输入长度" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="required" label="是否必填">
            <Select placeholder="请选择">
              <Option value={true}>是</Option>
              <Option value={false}>否</Option>
            </Select>
          </Form.Item>
          <Form.Item name="defaultValue" label="默认值">
            <Input placeholder="请输入默认值" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} placeholder="请输入字段描述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看详情模态框 */}
      <Modal
        title="字段详情"
        open={isViewModalOpen}
        onCancel={() => setIsViewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsViewModalOpen(false)}>关闭</Button>
        ]}
      >
        {currentField && (
          <Descriptions column={2}>
            <Descriptions.Item label="字段名称">{currentField.name}</Descriptions.Item>
            <Descriptions.Item label="字段类型">
              {fieldTypes.find(t => t.value === currentField.type)?.label || currentField.type}
            </Descriptions.Item>
            <Descriptions.Item label="长度">{currentField.length || '-'}</Descriptions.Item>
            <Descriptions.Item label="是否必填">{currentField.required ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="默认值">{currentField.defaultValue || '-'}</Descriptions.Item>
            <Descriptions.Item label="所属实体">
              {entities.find(e => e.id === currentField.masterDataEntityId)?.name || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{currentField.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentField.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{currentField.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default FieldManagement;
