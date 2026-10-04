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
  Upload,
  DatePicker,
  InputNumber,
  Switch,
} from 'antd';
import dayjs from 'dayjs';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, DownloadOutlined,
  CheckCircleOutlined, InboxOutlined, SendOutlined, AuditOutlined, CloseCircleOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { ColumnsType } from 'antd/es/table';
import type {
  MasterDataRecord,
  MasterDataField,
  CreateMasterDataRecordReq,
  UpdateMasterDataRecordReq,
  ImportDuplicateStrategy,
} from '../types';
import { masterDataRecordApi, masterDataFieldApi, approvalApi } from '../services/api';
import { normalizeTotal } from '@bone/shared-utils';
import { useEntityScope } from '../context/EntityScopeContext';
import EntityScopeSelect from '../components/EntityScopeSelect';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

/**
 * 后端返回的 record.data 是 JSON 字符串（MasterDataRecordDTO.data 为 String），
 * 而共享类型声明为对象；这里统一归一化为对象，避免动态列取不到值显示为 '-'。
 */
const parseRecordData = (data: unknown): Record<string, unknown> => {
  if (!data) {
    return {};
  }
  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data);
      return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
    } catch {
      return {};
    }
  }
  return typeof data === 'object' ? (data as Record<string, unknown>) : {};
};

/** 业务主键/生效期固定表单键：与动态字段 data 的键隔离，提交时单独拆出。 */
const BK = {
  recordCode: '__recordCode',
  displayName: '__displayName',
  effectiveFrom: '__effectiveFrom',
  effectiveTo: '__effectiveTo'
} as const;

const toPayloadDateTime = (v: unknown): string | undefined => {
  if (!v) return undefined;
  return dayjs(v as never).format('YYYY-MM-DD HH:mm:ss');
};

const RecordManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [currentRecord, setCurrentRecord] = useState<MasterDataRecord | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<MasterDataRecord[]>([]);
  const [fields, setFields] = useState<MasterDataField[]>([]);
  // 模型选择已提升为全局作用域（EntityScopeContext），跨页面共享
  const { entityId: selectedEntityId } = useEntityScope();
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  // 仅看当前生效（当前版本 + 生效窗口含此刻）：价格/客户类主数据消费的真实视角
  const [onlyCurrent, setOnlyCurrent] = useState<boolean>(false);
  const [importLoading, setImportLoading] = useState(false);
  // 导入重复策略：FAIL 重码行报失败（默认）；UPDATE 按编码幂等更新（ERP 周期同步场景）
  const [importDuplicateStrategy, setImportDuplicateStrategy] = useState<ImportDuplicateStrategy>('FAIL');
  // 审批流（UC-T7）：驳回弹窗与版本历史
  const [rejectTarget, setRejectTarget] = useState<MasterDataRecord | null>(null);
  const [rejectComment, setRejectComment] = useState('');
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versions, setVersions] = useState<Record<string, unknown>[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);

  const fetchFields = useCallback(async (entityId: string) => {
    try {
      const response = await masterDataFieldApi.listByEntityId(entityId);
      if (response.code === 200) {
        setFields(response.data);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取字段列表失败');
    }
  }, []);

  const fetchRecords = useCallback(async () => {
    if (!selectedEntityId) return;
    setLoading(true);
    try {
      const response = await masterDataRecordApi.page({
        masterDataEntityId: selectedEntityId,
        status: selectedStatus,
        keyword: keyword.trim() || undefined,
        onlyCurrent: onlyCurrent || undefined,
        pageNum: page,
        pageSize: pageSize
      });
      if (response.code === 200) {
        // 权威字段是 records；list 是后端 PageResult 的 @Deprecated 兼容 getter，
        // 将在 @JsonIgnore 收敛后消失（Bone-API-规范 §5.3）。此处刻意只读 records。
        setRecords(response.data.records);
        setTotal(normalizeTotal(response.data.total));
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取记录列表失败');
    } finally {
      setLoading(false);
    }
  }, [selectedEntityId, selectedStatus, keyword, onlyCurrent, page, pageSize]);

  useEffect(() => {
    if (selectedEntityId) {
      void fetchFields(selectedEntityId);
      void fetchRecords();
    }
  }, [selectedEntityId, fetchFields, fetchRecords]);

  // 打开创建模态框
  const handleAdd = () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个模型');
      return;
    }
    setIsEditMode(false);
    setCurrentRecord(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  // 打开编辑模态框
  const handleEdit = (record: MasterDataRecord) => {
    setIsEditMode(true);
    setCurrentRecord(record);
    // record.data 是 JSON 字符串，且键以字段 code 为准（与表单 name 对齐）
    form.setFieldsValue({
      ...parseRecordData(record.data),
      [BK.recordCode]: record.recordCode,
      [BK.displayName]: record.displayName,
      [BK.effectiveFrom]: record.effectiveFrom ? dayjs(record.effectiveFrom) : undefined,
      [BK.effectiveTo]: record.effectiveTo ? dayjs(record.effectiveTo) : undefined
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id: string): Promise<void> => {
    try {
      const response = await masterDataRecordApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        void fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('删除失败');
    }
  };

  // 发布记录
  const handlePublish = async (id: string) => {
    try {
      const response = await masterDataRecordApi.publish(id);
      if (response.code === 200) {
        message.success('发布成功');
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('发布失败');
    }
  };

  // 提交审批（UC-T7）
  const handleSubmitApproval = async (id: string) => {
    try {
      const response = await approvalApi.submit(id);
      if (response.code === 200) {
        message.success('已提交审批');
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('提交审批失败');
    }
  };

  // 审批通过（SoD：审批人≠提交人，后端校验）
  const handleApprove = async (id: string) => {
    try {
      const response = await approvalApi.approve(id);
      if (response.code === 200) {
        message.success('审批通过');
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('审批操作失败');
    }
  };

  // 审批驳回（退回草稿）
  const handleReject = async () => {
    if (!rejectTarget) return;
    try {
      const response = await approvalApi.reject(rejectTarget.id, rejectComment || undefined);
      if (response.code === 200) {
        message.success('已驳回，退回草稿');
        setRejectTarget(null);
        setRejectComment('');
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('驳回失败');
    }
  };

  // 版本历史（UC-T7 追溯）
  const handleShowVersions = async (id: string) => {
    setVersionsOpen(true);
    setVersionsLoading(true);
    try {
      const response = await approvalApi.versions(id);
      if (response.code === 200) {
        setVersions(Array.isArray(response.data) ? response.data : []);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取版本历史失败');
    } finally {
      setVersionsLoading(false);
    }
  };

  // 归档记录
  const handleArchive = async (id: string) => {
    try {
      const response = await masterDataRecordApi.archive(id);
      if (response.code === 200) {
        message.success('归档成功');
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('归档失败');
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields() as Record<string, unknown>;
      // 业务主键与生效期不属于 data，单独传给后端写 record_code / display_name / effective_* 列
      const { [BK.recordCode]: recordCode, [BK.displayName]: displayName,
        [BK.effectiveFrom]: effFrom, [BK.effectiveTo]: effTo, ...data } = values;
      const businessKey = {
        recordCode: recordCode as string | undefined,
        displayName: displayName as string | undefined,
        effectiveFrom: toPayloadDateTime(effFrom),
        effectiveTo: toPayloadDateTime(effTo)
      };
      let response;
      if (isEditMode && currentRecord) {
        const payload: UpdateMasterDataRecordReq = { data, ...businessKey };
        response = await masterDataRecordApi.update(currentRecord.id, payload);
      } else {
        if (!selectedEntityId) {
          message.error('请选择模型');
          return;
        }
        const payload: CreateMasterDataRecordReq = { data, ...businessKey };
        response = await masterDataRecordApi.create(selectedEntityId, payload);
      }
      if (response.code === 200) {
        message.success(isEditMode ? '更新成功' : '创建成功');
        setIsModalOpen(false);
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 导入记录
  const handleImport = async (file: File) => {
    if (!selectedEntityId) {
      message.warning('请先选择一个模型');
      return false;
    }
    setImportLoading(true);
    try {
      const response = await masterDataRecordApi.import(selectedEntityId, file, importDuplicateStrategy);
      if (response.code === 200) {
        const { total, successCount, updatedCount, failureCount, failures } = response.data;
        if (failureCount === 0) {
          const updatedPart = updatedCount ? `，更新 ${updatedCount} 条` : '';
          message.success(`导入完成：共 ${total} 条，新增 ${successCount} 条${updatedPart}`);
        } else {
          message.warning(
            `部分成功：共 ${total} 条，新增 ${successCount} 条${updatedCount ? `，更新 ${updatedCount} 条` : ''}，失败 ${failureCount} 条`,
          );
          (failures ?? []).slice(0, 5).forEach(f => {
            message.error(`第 ${f.rowNumber} 行${f.recordCode ? `（${f.recordCode}）` : ''}：${f.reason}`);
          });
        }
        fetchRecords();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('导入失败');
    } finally {
      setImportLoading(false);
    }
    return false;
  };

  // 导出记录
  const handleExport = async () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个模型');
      return;
    }
    try {
      const text = await masterDataRecordApi.export(selectedEntityId);
      const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      // 后端返回纯文本（逐行 `id=.., data=..`），非 Excel；扩展名须与真实内容一致
      a.download = `master-data-${selectedEntityId}-${new Date().getTime()}.txt`;
      a.click();
      window.URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch (error) {
      message.error('导出失败');
    }
  };

  // 状态标签（六态状态机：DRAFT→PENDING_APPROVAL→APPROVED→PUBLISHED→ARCHIVED，驳回退回 DRAFT）
  const getStatusTag = (status: string) => {
    switch (status) {
    case 'DRAFT':
      return <Tag color="blue">草稿</Tag>;
    case 'PENDING_APPROVAL':
      return <Tag color="orange">待审批</Tag>;
    case 'APPROVED':
      return <Tag color="cyan">审批通过</Tag>;
    case 'PUBLISHED':
      return <Tag color="green">已发布</Tag>;
    case 'SUPERSEDED':
      return <Tag color="purple">已被新版本取代</Tag>;
    case 'ARCHIVED':
      return <Tag color="gray">已归档</Tag>;
    default:
      return <Tag>{status}</Tag>;
    }
  };

  // 生效期标签：未生效 / 生效中 / 已失效（与后端 MasterDataRecord#isEffectiveNow 同语义）
  const getEffectiveTag = (record: MasterDataRecord) => {
    const now = dayjs();
    const from = record.effectiveFrom ? dayjs(record.effectiveFrom) : null;
    const to = record.effectiveTo ? dayjs(record.effectiveTo) : null;
    if (from && now.isBefore(from)) {
      return <Tag color="gold">{`${from.format('YYYY-MM-DD')} 起生效`}</Tag>;
    }
    if (to && now.isAfter(to)) {
      return <Tag color="red">已失效</Tag>;
    }
    if (!from && !to) {
      return <Tag>长期有效</Tag>;
    }
    return <Tag color="green">生效中</Tag>;
  };

  // 动态生成表单字段
  const generateFormFields = () => {
    return fields.map(field => {
      let formItem;
      switch (field.type) {
      case 'STRING':
      case 'TEXT':
        formItem = field.type === 'TEXT' ? (
          <TextArea rows={4} placeholder={`请输入${field.name}`} />
        ) : (
          <Input placeholder={`请输入${field.name}`} />
        );
        break;
      case 'NUMBER':
        // 建模期声明的 [minValue, maxValue] 在这里落到输入控件，
        // 让"单价必须 > 0""在录入时就拦住，而不是等提交后吃一个 400。
        formItem = (
          <InputNumber
            style={{ width: '100%' }}
            placeholder={`请输入${field.name}`}
            min={field.minValue}
            max={field.maxValue}
          />
        );
        break;
      case 'DATE':
        formItem = <Input type="date" placeholder={`请选择${field.name}`} />;
        break;
      case 'BOOLEAN':
        formItem = (
          <Select placeholder={`请选择${field.name}`}>
            <Option value={true}>是</Option>
            <Option value={false}>否</Option>
          </Select>
        );
        break;
      default:
        formItem = <Input placeholder={`请输入${field.name}`} />;
      }
      return (
        <Form.Item
          key={field.id}
          // 后端按字段 code 校验与存储（RecordDataValidator 以 code 取值），
          // 表单键必须与 code 对齐，否则写入数据会被判为缺字段。
          name={field.code ?? field.name}
          label={field.name}
          rules={field.required ? [{ required: true, message: `请输入${field.name}` }] : []}
        >
          {formItem}
        </Form.Item>
      );
    });
  };

  // 动态生成表格列
  const generateTableColumns = (): ColumnsType<MasterDataRecord> => {
    const columns: ColumnsType<MasterDataRecord> = [
      {
        title: '业务编码',
        dataIndex: 'recordCode',
        key: 'recordCode',
        width: 150,
        render: (code: string) => code || <span style={{ color: '#bfbfbf' }}>未补登</span>
      },
      {
        title: '名称',
        dataIndex: 'displayName',
        key: 'displayName',
        width: 160,
        ellipsis: true,
        render: (name: string) => name || '-'
      },
      {
        title: '记录ID',
        dataIndex: 'id',
        key: 'id'
      }
    ];

    fields.forEach(field => {
      const fieldKey = field.code ?? field.name;
      columns.push({
        title: field.name,
        dataIndex: `data.${fieldKey}`,
        key: fieldKey,
        ellipsis: true,
        // 后端 MasterDataRecordDTO.data 是 JSON 字符串，且键以字段 code 为准；
        // 直接用 dataIndex 取不到值，这里显式解析并兼容 code/name 两种键。
        render: (_: unknown, record: MasterDataRecord) => {
          const map = parseRecordData(record.data);
          const value = map[fieldKey] ?? map[field.name];
          return value === undefined || value === null || value === ''
            ? '-'
            : String(value);
        }
      });
    });

    columns.push(
      {
        title: '生效期',
        key: 'effective',
        width: 130,
        render: (_: unknown, record: MasterDataRecord) => getEffectiveTag(record)
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        render: (status: string) => getStatusTag(status)
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        key: 'createdAt'
      },
      {
        title: '操作',
        key: 'action',
        render: (_: unknown, record: MasterDataRecord) => (
          <Space size="middle" wrap>
            {record.status === 'DRAFT' && (
              <>
                <Button
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
                <Button
                  icon={<SendOutlined />}
                  onClick={() => handleSubmitApproval(record.id)}
                >
                  提交审批
                </Button>
                <Button
                  icon={<CheckCircleOutlined />}
                  onClick={() => handlePublish(record.id)}
                >
                  直接发布
                </Button>
              </>
            )}
            {record.status === 'PENDING_APPROVAL' && (
              <>
                <Popconfirm
                  title="确认审批通过？"
                  onConfirm={() => handleApprove(record.id)}
                  okText="通过"
                  cancelText="取消"
                >
                  <Button type="primary" icon={<AuditOutlined />}>审批通过</Button>
                </Popconfirm>
                <Button danger icon={<CloseCircleOutlined />} onClick={() => setRejectTarget(record)}>
                  驳回
                </Button>
              </>
            )}
            {record.status === 'APPROVED' && (
              <Button
                icon={<CheckCircleOutlined />}
                onClick={() => handlePublish(record.id)}
              >
                发布
              </Button>
            )}
            {record.status === 'PUBLISHED' && (
              <Button
                icon={<InboxOutlined />}
                onClick={() => handleArchive(record.id)}
              >
                归档
              </Button>
            )}
            <Button
              icon={<HistoryOutlined />}
              onClick={() => handleShowVersions(record.id)}
            >
              版本
            </Button>
          </Space>
        )
      }
    );

    return columns;
  };

  return (
    <div style={{ padding: '20px' }}>
      <Card title="主数据记录管理">
        {/* 模型选择和操作按钮 */}
        <Form layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item label="选择模型">
            <EntityScopeSelect showStatus={false} />
          </Form.Item>
          <Form.Item label="状态">
            <Select
              style={{ width: 150 }}
              placeholder="请选择状态"
              value={selectedStatus || undefined}
              onChange={setSelectedStatus}
            >
              <Option value="">全部</Option>
              <Option value="DRAFT">草稿</Option>
              <Option value="PENDING_APPROVAL">待审批</Option>
              <Option value="APPROVED">审批通过</Option>
              <Option value="PUBLISHED">已发布</Option>
              <Option value="SUPERSEDED">已被新版本取代</Option>
              <Option value="ARCHIVED">已归档</Option>
            </Select>
          </Form.Item>
          <Form.Item label="关键字">
            <Input.Search
              style={{ width: 260 }}
              placeholder="搜业务编码 / 名称 / 记录内容"
              allowClear
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(1);
              }}
            />
          </Form.Item>
          <Form.Item label="仅当前生效" style={{ marginBottom: 0 }}>
            <Switch
              checked={onlyCurrent}
              onChange={(v) => {
                setOnlyCurrent(v);
                setPage(1);
              }}
            />
          </Form.Item>
          <Form.Item label="导入重复策略">
            <Select
              style={{ width: 170 }}
              value={importDuplicateStrategy}
              onChange={setImportDuplicateStrategy}
            >
              <Option value="FAIL">重复时报错（默认）</Option>
              <Option value="UPDATE">重复时更新</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              创建记录
            </Button>
          </Form.Item>
          <Form.Item>
            <Upload
              showUploadList={false}
              beforeUpload={handleImport}
              maxCount={1}
            >
              <Button icon={<UploadOutlined />} loading={importLoading}>
                导入
              </Button>
            </Upload>
          </Form.Item>
          <Form.Item>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              导出
            </Button>
          </Form.Item>
        </Form>

        {/* 记录列表 */}
        <ProTable
          options={false}
          columns={generateTableColumns() as ProColumns<MasterDataRecord>[]}
          dataSource={records}
          loading={loading}
          // 记录列随模型字段动态增长，列宽合计可能超过容器；
          // 不设 scroll 会让整页横向滚动，这里改为表格内部横向滚动。
          scroll={{ x: 'max-content' }}
          pagination={{
            total,
            pageSize,
            current: page,
            onChange: (current, size) => {
              setPage(current);
              setPageSize(size);
            }
          }}
          locale={{ emptyText: '请先选择一个模型' }}
        />
      </Card>

      {/* 创建/编辑模态框 */}
      <Modal
        title={isEditMode ? '编辑记录' : '创建记录'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        width={800}
      >
        <Form form={form} layout="vertical">
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name={BK.recordCode}
              label="业务编码"
              style={{ flex: 1 }}
              extra="租户+模型内唯一，下游按此定位记录"
            >
              <Input placeholder="如 PRD-1001，留空则后续补登" />
            </Form.Item>
            <Form.Item
              name={BK.displayName}
              label="名称"
              style={{ flex: 1 }}
            >
              <Input placeholder="列表页直接展示的名称" />
            </Form.Item>
          </Space>
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item
              name={BK.effectiveFrom}
              label="生效开始"
              style={{ flex: 1 }}
            >
              <DatePicker showTime style={{ width: '100%' }} placeholder="留空表示立即生效" />
            </Form.Item>
            <Form.Item
              name={BK.effectiveTo}
              label="生效结束"
              style={{ flex: 1 }}
            >
              <DatePicker showTime style={{ width: '100%' }} placeholder="留空表示长期有效" />
            </Form.Item>
          </Space>
          {generateFormFields()}
        </Form>
      </Modal>

      {/* 查看详情模态框 */}
      <Modal
        title="记录详情"
        open={isViewModalOpen}
        onCancel={() => setIsViewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsViewModalOpen(false)}>关闭</Button>
        ]}
        width={800}
      >
        {currentRecord && (
          <div>
            <Descriptions column={2}>
              {fields.map(field => {
                const map = parseRecordData(currentRecord.data);
                const key = field.code ?? field.name;
                const value = map[key] ?? map[field.name];
                return (
                  <Descriptions.Item key={field.id} label={field.name}>
                    {value === undefined || value === null ? '-' : String(value)}
                  </Descriptions.Item>
                );
              })}
              <Descriptions.Item label="状态">{getStatusTag(currentRecord.status)}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{currentRecord.createdAt}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{currentRecord.updatedAt}</Descriptions.Item>
              {currentRecord.publishTime && (
                <Descriptions.Item label="发布时间">{currentRecord.publishTime}</Descriptions.Item>
              )}
            </Descriptions>
          </div>
        )}
      </Modal>

      {/* 驳回弹窗 */}
      <Modal
        title="驳回审批"
        open={!!rejectTarget}
        onOk={handleReject}
        onCancel={() => { setRejectTarget(null); setRejectComment(''); }}
        okText="确认驳回"
        cancelText="取消"
      >
        <Input.TextArea
          rows={3}
          placeholder="填写驳回原因（可空）"
          value={rejectComment}
          onChange={(e) => setRejectComment(e.target.value)}
        />
      </Modal>

      {/* 版本历史弹窗 */}
      <Modal
        title="版本历史"
        open={versionsOpen}
        footer={[<Button key="close" onClick={() => setVersionsOpen(false)}>关闭</Button>]}
        onCancel={() => setVersionsOpen(false)}
        width={720}
      >
        <ProTable
          options={false}
          search={false}
          rowKey={(r: Record<string, unknown>) => String(r.id ?? r.versionNumber)}
          loading={versionsLoading}
          columns={[
            { title: '版本号', dataIndex: 'versionNumber', key: 'versionNumber' },
            { title: '数据快照', dataIndex: 'data', key: 'data', ellipsis: true },
            { title: '操作人', dataIndex: 'createdBy', key: 'createdBy' },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
          ]}
          dataSource={versions as never[]}
          pagination={false}
          locale={{ emptyText: '暂无版本记录' }}
        />
      </Modal>
    </div>
  );
};

export default RecordManagement;
