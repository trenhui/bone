import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  message,
  Popconfirm,
  Space,
  Tag,
  Descriptions,
  Progress,
  Result
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type {
  DataQualityRule,
  MasterDataEntity,
  CreateDataQualityRuleReq,
  UpdateDataQualityRuleReq,
  QualityCheck,
  QualityReport
} from '../types';
import { dataQualityRuleApi, masterDataEntityApi, qualityCheckApi } from '../services/api';

const { Option } = Select;
const { TextArea } = Input;

const QualityRuleManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [isCheckModalOpen, setIsCheckModalOpen] = useState(false);
  const [currentRule, setCurrentRule] = useState<DataQualityRule | null>(null);
  const [currentCheck, setCurrentCheck] = useState<QualityCheck | null>(null);
  const [currentReport, setCurrentReport] = useState<QualityReport | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [checkLoading, setCheckLoading] = useState(false);
  const [rules, setRules] = useState<DataQualityRule[]>([]);
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<number | null>(null);
  const [qualityChecks, setQualityChecks] = useState<QualityCheck[]>([]);

  // 获取实体列表
  const fetchEntities = async () => {
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
    } catch (error) {
      message.error('获取实体列表失败');
    }
  };

  // 获取规则列表
  const fetchRules = async () => {
    setLoading(true);
    try {
      const response = await dataQualityRuleApi.page({ masterDataEntityId: selectedEntityId });
      if (response.code === 200) {
        setRules(response.data.list);
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('获取规则列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 获取质量检查历史
  const fetchQualityChecks = async () => {
    try {
      const response = await qualityCheckApi.page({ masterDataEntityId: selectedEntityId });
      if (response.code === 200) {
        setQualityChecks(response.data.list);
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('获取质量检查历史失败');
    }
  };

  useEffect(() => {
    fetchEntities();
  }, []);

  useEffect(() => {
    if (selectedEntityId) {
      fetchRules();
      fetchQualityChecks();
    }
  }, [selectedEntityId]);

  // 打开创建模态框
  const handleAdd = () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个实体');
      return;
    }
    setIsEditMode(false);
    setCurrentRule(null);
    form.resetFields();
    form.setFieldsValue({ masterDataEntityId: selectedEntityId });
    setIsModalOpen(true);
  };

  // 打开编辑模态框
  const handleEdit = (record: DataQualityRule) => {
    setIsEditMode(true);
    setCurrentRule(record);
    form.setFieldsValue({
      name: record.name,
      type: record.type,
      expression: record.expression,
      severity: record.severity,
      description: record.description
    });
    setIsModalOpen(true);
  };

  // 打开查看模态框
  const handleView = (record: DataQualityRule) => {
    setCurrentRule(record);
    setIsViewModalOpen(true);
  };

  // 删除规则
  const handleDelete = async (id: number) => {
    try {
      const response = await dataQualityRuleApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        fetchRules();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  // 执行质量检查
  const handleExecuteCheck = async () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个实体');
      return;
    }
    setCheckLoading(true);
    setIsCheckModalOpen(true);
    try {
      const response = await dataQualityRuleApi.executeCheck(selectedEntityId);
      if (response.code === 200) {
        setCurrentCheck(response.data);
        // 轮询检查结果
        const checkInterval = setInterval(async () => {
          const checkResponse = await qualityCheckApi.detail(response.data.id);
          if (checkResponse.code === 200) {
            setCurrentCheck(checkResponse.data);
            if (checkResponse.data.status === 'COMPLETED' || checkResponse.data.status === 'FAILED') {
              clearInterval(checkInterval);
              if (checkResponse.data.status === 'COMPLETED') {
                const reportResponse = await dataQualityRuleApi.getCheckResult(checkResponse.data.id);
                if (reportResponse.code === 200) {
                  setCurrentReport(reportResponse.data);
                }
              }
            }
          }
        }, 2000);
      } else {
        message.error(response.message);
        setIsCheckModalOpen(false);
      }
    } catch (error) {
      message.error('执行质量检查失败');
      setIsCheckModalOpen(false);
    } finally {
      setCheckLoading(false);
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      let response;
      if (isEditMode && currentRule) {
        response = await dataQualityRuleApi.update(currentRule.id, values);
      } else {
        response = await dataQualityRuleApi.create(values as CreateDataQualityRuleReq);
      }
      if (response.code === 200) {
        message.success(isEditMode ? '更新成功' : '创建成功');
        setIsModalOpen(false);
        fetchRules();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 规则类型选项
  const ruleTypes = [
    { value: 'UNIQUE', label: '唯一性' },
    { value: 'FORMAT', label: '格式' },
    { value: 'RANGE', label: '范围' },
    { value: 'REFERENCE', label: '引用' },
    { value: 'CUSTOM', label: '自定义' }
  ];

  // 严重程度选项
  const severityOptions = [
    { value: 'ERROR', label: '错误', color: 'red' },
    { value: 'WARNING', label: '警告', color: 'orange' },
    { value: 'INFO', label: '信息', color: 'blue' }
  ];

  // 状态标签
  const getStatusTag = (status: string) => {
    switch (status) {
      case 'ERROR':
        return <Tag color="red">错误</Tag>;
      case 'WARNING':
        return <Tag color="orange">警告</Tag>;
      case 'INFO':
        return <Tag color="blue">信息</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '规则名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: DataQualityRule) => (
        <a onClick={() => handleView(record)}>{text}</a>
      )
    },
    {
      title: '规则类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const typeMap = {
          UNIQUE: '唯一性',
          FORMAT: '格式',
          RANGE: '范围',
          REFERENCE: '引用',
          CUSTOM: '自定义'
        };
        return typeMap[type as keyof typeof typeMap] || type;
      }
    },
    {
      title: '表达式',
      dataIndex: 'expression',
      key: 'expression',
      ellipsis: true
    },
    {
      title: '严重程度',
      dataIndex: 'severity',
      key: 'severity',
      render: (severity: string) => getStatusTag(severity)
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
      render: (_: any, record: DataQualityRule) => (
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
      <Card title="数据质量规则管理">
        {/* 实体选择和执行检查 */}
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
              创建规则
            </Button>
          </Form.Item>
          <Form.Item>
            <Button type="default" icon={<PlayCircleOutlined />} onClick={handleExecuteCheck}>
              执行质量检查
            </Button>
          </Form.Item>
        </Form>

        {/* 规则列表 */}
        <ProTable
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: '请先选择一个实体' }}
        />

        {/* 质量检查历史 */}
        <div style={{ marginTop: 24 }}>
          <h3>质量检查历史</h3>
          <ProTable
            columns={[
              {
                title: '检查ID',
                dataIndex: 'id',
                key: 'id'
              },
              {
                title: '开始时间',
                dataIndex: 'startTime',
                key: 'startTime'
              },
              {
                title: '结束时间',
                dataIndex: 'endTime',
                key: 'endTime',
                render: (endTime: string) => endTime || '-'
              },
              {
                title: '状态',
                dataIndex: 'status',
                key: 'status',
                render: (status: string) => {
                  const statusMap = {
                    PENDING: <Tag color="blue">待处理</Tag>,
                    RUNNING: <Tag color="yellow">运行中</Tag>,
                    COMPLETED: <Tag color="green">已完成</Tag>,
                    FAILED: <Tag color="red">失败</Tag>
                  };
                  return statusMap[status as keyof typeof statusMap] || <Tag>{status}</Tag>;
                }
              },
              {
                title: '问题数量',
                dataIndex: 'issueCount',
                key: 'issueCount'
              }
            ]}
            dataSource={qualityChecks}
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: '暂无检查历史' }}
          />
        </div>
      </Card>

      {/* 创建/编辑模态框 */}
      <Modal
        title={isEditMode ? '编辑规则' : '创建规则'}
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
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="请输入规则名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="规则类型"
            rules={[{ required: true, message: '请选择规则类型' }]}
          >
            <Select placeholder="请选择规则类型">
              {ruleTypes.map(type => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="expression"
            label="规则表达式"
            rules={[{ required: true, message: '请输入规则表达式' }]}
          >
            <Input placeholder="请输入规则表达式" />
          </Form.Item>
          <Form.Item
            name="severity"
            label="严重程度"
            rules={[{ required: true, message: '请选择严重程度' }]}
          >
            <Select placeholder="请选择严重程度">
              {severityOptions.map(option => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} placeholder="请输入规则描述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看详情模态框 */}
      <Modal
        title="规则详情"
        open={isViewModalOpen}
        onCancel={() => setIsViewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsViewModalOpen(false)}>关闭</Button>
        ]}
      >
        {currentRule && (
          <Descriptions column={2}>
            <Descriptions.Item label="规则名称">{currentRule.name}</Descriptions.Item>
            <Descriptions.Item label="规则类型">
              {ruleTypes.find(t => t.value === currentRule.type)?.label || currentRule.type}
            </Descriptions.Item>
            <Descriptions.Item label="规则表达式" span={2}>{currentRule.expression}</Descriptions.Item>
            <Descriptions.Item label="严重程度">{getStatusTag(currentRule.severity)}</Descriptions.Item>
            <Descriptions.Item label="所属实体">
              {entities.find(e => e.id === currentRule.masterDataEntityId)?.name || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{currentRule.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentRule.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{currentRule.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* 质量检查结果模态框 */}
      <Modal
        title="质量检查结果"
        open={isCheckModalOpen}
        onCancel={() => setIsCheckModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsCheckModalOpen(false)}>关闭</Button>
        ]}
        width={800}
      >
        {currentCheck && (
          <div>
            <Descriptions column={2}>
              <Descriptions.Item label="检查ID">{currentCheck.id}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {currentCheck.status === 'PENDING' && <Tag color="blue">待处理</Tag>}
                {currentCheck.status === 'RUNNING' && <Tag color="yellow">运行中</Tag>}
                {currentCheck.status === 'COMPLETED' && <Tag color="green">已完成</Tag>}
                {currentCheck.status === 'FAILED' && <Tag color="red">失败</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="开始时间">{currentCheck.startTime}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{currentCheck.endTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="问题数量" span={2}>{currentCheck.issueCount}</Descriptions.Item>
            </Descriptions>
            
            {currentCheck.status === 'RUNNING' && (
              <div style={{ marginTop: 20 }}>
                <Progress percent={50} status="active" />
                <p style={{ textAlign: 'center', marginTop: 10 }}>正在执行质量检查...</p>
              </div>
            )}
            
            {currentCheck.status === 'COMPLETED' && currentReport && (
              <div style={{ marginTop: 20 }}>
                <Result
                  status="success"
                  title="质量检查完成"
                  subTitle={`共发现 ${currentReport.issueCount} 个问题`}
                  extra={[
                    <Button key="detail" type="primary">
                      查看详细报告
                    </Button>
                  ]}
                />
              </div>
            )}
            
            {currentCheck.status === 'FAILED' && (
              <div style={{ marginTop: 20 }}>
                <Result
                  status="error"
                  title="质量检查失败"
                  subTitle="请检查系统配置或联系管理员"
                />
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default QualityRuleManagement;
