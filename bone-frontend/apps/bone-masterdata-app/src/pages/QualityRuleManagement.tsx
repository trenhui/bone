import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
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
  Progress,
  Result
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  DataQualityRule,
  MasterDataEntity,
  CreateDataQualityRuleReq,
  QualityCheck,
  QualityReport,
  QualityReportData
} from '../types';
import {
  dataQualityRuleApi,
  masterDataEntityApi,
  qualityCheckApi,
  qualityReportApi
} from '../services/api';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

const QualityRuleManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [isCheckModalOpen, setIsCheckModalOpen] = useState(false);
  const [currentRule, setCurrentRule] = useState<DataQualityRule | null>(null);
  const [currentCheck, setCurrentCheck] = useState<QualityCheck | null>(null);
  const [currentReport, setCurrentReport] = useState<QualityReport | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [, setCheckLoading] = useState(false);
  const [rules, setRules] = useState<DataQualityRule[]>([]);
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<number | null>(null);
  const [qualityChecks, setQualityChecks] = useState<QualityCheck[]>([]);

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

  const fetchRules = useCallback(async () => {
    setLoading(true);
    try {
      const response = await dataQualityRuleApi.page({
        masterDataEntityId: selectedEntityId ?? undefined,
      });
      if (response.code === 200) {
        setRules(response.data.list);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取规则列表失败');
    } finally {
      setLoading(false);
    }
  }, [selectedEntityId]);

  const fetchQualityChecks = useCallback(async () => {
    try {
      const response = await qualityCheckApi.list({
        masterDataEntityId: selectedEntityId ?? undefined,
      });
      if (response.code === 200) {
        setQualityChecks(response.data ?? []);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取质量检查历史失败');
    }
  }, [selectedEntityId]);

  useEffect(() => {
    void fetchEntities();
  }, [fetchEntities]);

  useEffect(() => {
    if (selectedEntityId) {
      void fetchRules();
      void fetchQualityChecks();
    }
  }, [selectedEntityId, fetchRules, fetchQualityChecks]);

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

  // 执行质量检查（后端同步执行并落报告，无需轮询）
  const handleExecuteCheck = async () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个实体');
      return;
    }
    setCheckLoading(true);
    try {
      const response = await dataQualityRuleApi.executeCheck(selectedEntityId);
      if (response.code !== 200) {
        message.error(response.message);
        return;
      }
      const checkId = response.data;
      const checkResponse = await qualityCheckApi.detail(checkId);
      if (checkResponse.code === 200) {
        setCurrentCheck(checkResponse.data);
        const reportResponse = await qualityReportApi.listByCheckId(checkId);
        if (reportResponse.code === 200) {
          setCurrentReport(reportResponse.data?.[0] ?? null);
        }
        setIsCheckModalOpen(true);
        void fetchQualityChecks();
      } else {
        message.error(checkResponse.message);
      }
    } catch {
      message.error('执行质量检查失败');
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

  /**
   * 规则类型与表达式示例。
   *
   * 取值与后端 RuleExpressionEvaluator 一致：前 5 类会真实求值，CUSTOM 允许录入但在报告中标注「未求值」。
   */
  const ruleTypes = [
    { value: 'NOT_NULL', label: '非空', hint: 'field=code' },
    { value: 'UNIQUE', label: '唯一性', hint: 'field=code' },
    { value: 'FORMAT', label: '格式', hint: 'field=zip;pattern=^\\d{6}$' },
    { value: 'RANGE', label: '范围', hint: 'field=amount;min=0;max=1000000' },
    { value: 'REFERENCE', label: '引用', hint: 'field=dept;entity=200;targetField=code' },
    { value: 'CUSTOM', label: '自定义（暂不求值）', hint: '暂不支持求值，检查时会在报告中标注' }
  ];

  // 严重程度选项：对齐后端 RuleSeverity 枚举（LOW/MEDIUM/HIGH/CRITICAL）
  const severityOptions = [
    { value: 'LOW', label: '低' },
    { value: 'MEDIUM', label: '中' },
    { value: 'HIGH', label: '高' },
    { value: 'CRITICAL', label: '严重' }
  ];

  // 严重程度标签
  const getStatusTag = (severity: string) => {
    const severityMap: Record<string, { color: string; text: string }> = {
      LOW: { color: 'default', text: '低' },
      MEDIUM: { color: 'blue', text: '中' },
      HIGH: { color: 'orange', text: '高' },
      CRITICAL: { color: 'red', text: '严重' }
    };
    const item = severityMap[severity];
    return item ? <Tag color={item.color}>{item.text}</Tag> : <Tag>{severity}</Tag>;
  };

  // 表达式示例随所选规则类型变化，避免用户写出后端无法求值的表达式
  const selectedRuleType = Form.useWatch('type', form);
  const expressionHint =
    ruleTypes.find(t => t.value === selectedRuleType)?.hint ?? '形如 field=字段编码;参数=值';

  // 后端 JSON 列可能回传字符串，统一归一化后再渲染
  const reportSummary = useMemo<QualityReportData | null>(() => {
    const raw = currentReport?.reportData;
    if (!raw) return null;
    try {
      const data = (
        typeof raw === 'string' ? JSON.parse(raw) : raw
      ) as Partial<QualityReportData>;
      return {
        entityId: data.entityId ?? 0,
        totalRecords: data.totalRecords ?? 0,
        rules: data.rules ?? [],
        unsupportedRules: data.unsupportedRules ?? []
      };
    } catch {
      return null;
    }
  }, [currentReport]);

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
      render: (type: string) => ruleTypes.find(t => t.value === type)?.label || type
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
      render: (_: unknown, record: DataQualityRule) => (
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
          columns={columns as ProColumns<DataQualityRule>[]}
          dataSource={rules}
          loading={loading}
          pagination={{ pageSize: 10 }}
          options={{ reload: false, density: false, setting: false }}
          locale={{ emptyText: '请先选择一个实体' }}
        />

        {/* 质量检查历史 */}
        <div style={{ marginTop: 24 }}>
          <h3>质量检查历史</h3>
          <ProTable
            options={{ reload: false, density: false, setting: false }}
            columns={[
              {
                title: '检查ID',
                dataIndex: 'id',
                key: 'id'
              },
              {
                title: '开始时间',
                dataIndex: 'startedAt',
                key: 'startedAt'
              },
              {
                title: '结束时间',
                dataIndex: 'endedAt',
                key: 'endedAt',
                render: (_: unknown, record: QualityCheck) => record.endedAt || '-'
              },
              {
                title: '总记录数',
                dataIndex: 'totalRecords',
                key: 'totalRecords',
                render: (_: unknown, record: QualityCheck) => record.totalRecords ?? 0
              },
              {
                title: '状态',
                dataIndex: 'status',
                key: 'status',
                render: (_, record) => {
                  const statusMap = {
                    PENDING: <Tag color="blue">待处理</Tag>,
                    RUNNING: <Tag color="yellow">运行中</Tag>,
                    COMPLETED: <Tag color="green">已完成</Tag>,
                    FAILED: <Tag color="red">失败</Tag>
                  };
                  return statusMap[record.status as keyof typeof statusMap] || <Tag>{record.status}</Tag>;
                }
              },
              {
                title: '未通过记录',
                dataIndex: 'failedRecords',
                key: 'failedRecords',
                render: (_: unknown, record: QualityCheck) => (
                  <Tag color={(record.failedRecords ?? 0) > 0 ? 'red' : 'green'}>
                    {record.failedRecords ?? 0}
                  </Tag>
                )
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
            extra={`示例：${expressionHint}`}
            rules={[{ required: true, message: '请输入规则表达式' }]}
          >
            <Input placeholder={expressionHint} />
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
              <Descriptions.Item label="开始时间">{currentCheck.startedAt}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{currentCheck.endedAt || '-'}</Descriptions.Item>
              <Descriptions.Item label="总记录数">{currentCheck.totalRecords ?? 0}</Descriptions.Item>
              <Descriptions.Item label="未通过记录">{currentCheck.failedRecords ?? 0}</Descriptions.Item>
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
                  status={currentReport.issueCount > 0 ? 'warning' : 'success'}
                  title="质量检查完成"
                  subTitle={`共发现 ${currentReport.issueCount} 个问题（总记录 ${
                    currentCheck.totalRecords ?? 0
                  } 条，未通过 ${currentCheck.failedRecords ?? 0} 条）`}
                />
                {reportSummary && (
                  <ProTable
                    size="small"
                    search={false}
                    options={false}
                    pagination={false}
                    headerTitle="规则命中情况"
                    rowKey="ruleId"
                    dataSource={reportSummary.rules}
                    columns={
                      [
                        { title: '规则', dataIndex: 'ruleName' },
                        { title: '类型', dataIndex: 'type' },
                        {
                          title: '命中问题数',
                          dataIndex: 'violations',
                          render: (v: number) => (
                            <Tag color={v > 0 ? 'red' : 'green'}>{v}</Tag>
                          )
                        },
                        {
                          title: '示例问题',
                          dataIndex: 'samples',
                          render: (samples: QualityReportData['rules'][number]['samples']) =>
                            samples.length > 0 ? samples[0].message : '-'
                        }
                      ] as ProColumns<QualityReportData['rules'][number]>[]
                    }
                  />
                )}
                {reportSummary && reportSummary.unsupportedRules.length > 0 && (
                  <Alert
                    style={{ marginTop: 16 }}
                    type="info"
                    showIcon
                    message="以下规则类型暂不支持求值，未计入结论"
                    description={reportSummary.unsupportedRules
                      .map(r => `${r.ruleName}（${r.type}）：${r.reason}`)
                      .join('；')}
                  />
                )}
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
