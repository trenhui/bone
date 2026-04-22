import React, { useState, useEffect } from 'react';
import { Card, Button, Input, Select, Form, message, Table, Modal, Popconfirm, Spin } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import ApiService from '../../services/apiService';
import defaultErrorHandler from '../../utils/errorHandler';

const { Option } = Select;
const { TextArea } = Input;

const ExtensionPointConfig = () => {
  const [form] = Form.useForm();
  const [configs, setConfigs] = useState([]);
  const [extensionPoints, setExtensionPoints] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  useEffect(() => {
    loadConfigData();
    loadExtensionPoints();
  }, []);

  const loadConfigData = async () => {
    try {
      setLoading(true);
      const response = await ApiService.extensionPointConfig.getExtensionPointConfigs();
      setConfigs(Array.isArray(response) ? response : []);
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'ExtensionPointConfig',
        operation: 'loadConfigData'
      });
      // 使用模拟数据
      setConfigs(getMockConfigs());
    } finally {
      setLoading(false);
    }
  };

  const loadExtensionPoints = async () => {
    try {
      const response = await ApiService.extPoint.getExtPoints({});
      setExtensionPoints(Array.isArray(response) ? response : []);
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'ExtensionPointConfig',
        operation: 'loadExtensionPoints'
      });
      // 使用模拟数据
      setExtensionPoints(getMockExtensionPoints());
    }
  };

  const getMockConfigs = () => [
    {
      id: '1',
      extPointId: 'payment',
      name: '支付配置',
      description: '默认支付配置',
      configValues: { timeout: 30000, retry: 3 },
      tenantCode: 'DEFAULT',
      bizCode: 'PAYMENT',
      scenario: 'NORMAL',
      priority: 100,
      enabled: true,
      createdBy: 'admin',
      updatedBy: 'admin',
      createdAt: Date.now() - 86400000,
      updatedAt: Date.now() - 3600000
    },
    {
      id: '2',
      extPointId: 'coupon',
      name: '优惠券配置',
      description: '优惠券处理配置',
      configValues: { maxDiscount: 50, minOrderAmount: 100 },
      tenantCode: 'DEFAULT',
      bizCode: 'ORDER',
      scenario: 'CREATE',
      priority: 90,
      enabled: true,
      createdBy: 'admin',
      updatedBy: 'admin',
      createdAt: Date.now() - 172800000,
      updatedAt: Date.now() - 7200000
    }
  ];

  const getMockExtensionPoints = () => [
    { id: 'payment', name: '支付扩展点' },
    { id: 'coupon', name: '优惠券扩展点' },
    { id: 'order', name: '订单扩展点' },
    { id: 'user', name: '用户扩展点' }
  ];

  const handleAdd = () => {
    setSelectedConfig(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setSelectedConfig(record);
    form.setFieldsValue({
      ...record,
      configValues: JSON.stringify(record.configValues, null, 2)
    });
    setModalVisible(true);
  };

  const handleDelete = async (record) => {
    try {
      setLoading(true);
      await ApiService.extensionPointConfig.deleteExtensionPointConfig(record.id);
      message.success('配置删除成功');
      loadConfigData();
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'ExtensionPointConfig',
        operation: 'handleDelete',
        configId: record.id
      });
      message.error('删除失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (values) => {
    try {
      setConfirmLoading(true);
      let configData = {
        ...values,
        configValues: JSON.parse(values.configValues)
      };

      if (selectedConfig) {
        await ApiService.extensionPointConfig.updateExtensionPointConfig(selectedConfig.id, configData);
        message.success('配置更新成功');
      } else {
        await ApiService.extensionPointConfig.createExtensionPointConfig(configData);
        message.success('配置创建成功');
      }
      setModalVisible(false);
      loadConfigData();
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'ExtensionPointConfig',
        operation: 'handleSubmit',
        configData: values
      });
      message.error('操作失败，请稍后重试');
    } finally {
      setConfirmLoading(false);
    }
  };

  const handleToggleStatus = async (record) => {
    try {
      setLoading(true);
      const updatedConfig = {
        ...record,
        enabled: !record.enabled
      };
      await ApiService.extensionPointConfig.updateExtensionPointConfig(record.id, updatedConfig);
      message.success(`配置已${updatedConfig.enabled ? '启用' : '禁用'}`);
      loadConfigData();
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'ExtensionPointConfig',
        operation: 'handleToggleStatus',
        configId: record.id
      });
      message.error('操作失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '扩展点',
      dataIndex: 'extPointId',
      key: 'extPointId',
      render: (extPointId) => {
        const extPoint = extensionPoints.find(ep => ep.id === extPointId);
        return extPoint ? extPoint.name : extPointId;
      }
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description'
    },
    {
      title: '租户',
      dataIndex: 'tenantCode',
      key: 'tenantCode'
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority'
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled) => (
        <span className={`status-badge ${enabled ? 'enabled' : 'disabled'}`}>
          {enabled ? '启用' : '禁用'}
        </span>
      )
    },
    {
      title: '操作',
      key: 'action',
      render: (text, record) => (
        <div className="action-buttons">
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          />
          <Popconfirm
            title="确定要删除这个配置吗？"
            onConfirm={() => handleDelete(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
          <Button
            type={record.enabled ? 'text' : 'primary'}
            onClick={() => handleToggleStatus(record)}
          >
            {record.enabled ? '禁用' : '启用'}
          </Button>
        </div>
      )
    }
  ];

  return (
    <div className="extension-point-config-container">
      <Card className="config-card">
        <div className="card-header">
          <h2>扩展点配置管理</h2>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
          >
            新建配置
          </Button>
        </div>

        {loading ? (
          <div className="loading-container">
            <Spin size="large" />
          </div>
        ) : (
          <Table
            columns={columns}
            dataSource={configs}
            rowKey="id"
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>

      <Modal
        title={selectedConfig ? '编辑配置' : '新建配置'}
        open={modalVisible}
        onOk={form.submit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={confirmLoading}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          <Form.Item
            name="extPointId"
            label="扩展点"
            rules={[{ required: true, message: '请选择扩展点' }]}
          >
            <Select placeholder="选择扩展点">
              {extensionPoints.map(extPoint => (
                <Option key={extPoint.id} value={extPoint.id}>
                  {extPoint.name}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label="配置名称"
            rules={[{ required: true, message: '请输入配置名称' }]}
          >
            <Input placeholder="输入配置名称" />
          </Form.Item>

          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea rows={3} placeholder="输入配置描述" />
          </Form.Item>

          <Form.Item
            name="tenantCode"
            label="租户"
            initialValue="DEFAULT"
          >
            <Input placeholder="输入租户编码" />
          </Form.Item>

          <Form.Item
            name="bizCode"
            label="业务域"
          >
            <Input placeholder="输入业务域编码" />
          </Form.Item>

          <Form.Item
            name="scenario"
            label="场景"
          >
            <Input placeholder="输入场景编码" />
          </Form.Item>

          <Form.Item
            name="priority"
            label="优先级"
            initialValue={100}
          >
            <Input type="number" placeholder="输入优先级" />
          </Form.Item>

          <Form.Item
            name="enabled"
            label="启用状态"
            valuePropName="checked"
            initialValue={true}
          >
            <input type="checkbox" />
          </Form.Item>

          <Form.Item
            name="configValues"
            label="配置值 (JSON格式)"
            rules={[{ required: true, message: '请输入配置值' }]}
            initialValue="{}"
          >
            <TextArea rows={6} placeholder="输入JSON格式的配置值" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ExtensionPointConfig;
