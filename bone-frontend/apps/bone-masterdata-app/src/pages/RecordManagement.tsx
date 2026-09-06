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
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, DownloadOutlined, CheckCircleOutlined, InboxOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { ColumnsType } from 'antd/es/table';
import type {
  MasterDataRecord,
  MasterDataEntity,
  MasterDataField,
} from '../types';
import { masterDataRecordApi, masterDataEntityApi, masterDataFieldApi } from '../services/api';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

const RecordManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [currentRecord, setCurrentRecord] = useState<MasterDataRecord | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<MasterDataRecord[]>([]);
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [fields, setFields] = useState<MasterDataField[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<number | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [importLoading, setImportLoading] = useState(false);

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
        pageNum: page,
        pageSize: pageSize
      });
      if (response.code === 200) {
        setRecords(response.data.list);
        setTotal(response.data.total);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取记录列表失败');
    } finally {
      setLoading(false);
    }
  }, [selectedEntityId, selectedStatus, page, pageSize]);

  useEffect(() => {
    void fetchEntities();
  }, [fetchEntities]);

  useEffect(() => {
    if (selectedEntityId) {
      void fetchFields(selectedEntityId);
      void fetchRecords();
    }
  }, [selectedEntityId, fetchFields, fetchRecords]);

  // 打开创建模态框
  const handleAdd = () => {
    if (!selectedEntityId) {
      message.warning('请先选择一个实体');
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
    form.setFieldsValue(record.data);
    setIsModalOpen(true);
  };

  const handleDelete = async (id: number): Promise<void> => {
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
  const handlePublish = async (id: number) => {
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

  // 归档记录
  const handleArchive = async (id: number) => {
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
      const values = await form.validateFields();
      let response;
      if (isEditMode && currentRecord) {
        response = await masterDataRecordApi.update(currentRecord.id, { data: values });
      } else {
        if (!selectedEntityId) {
          message.error('请选择实体');
          return;
        }
        response = await masterDataRecordApi.create(selectedEntityId, values);
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
      message.warning('请先选择一个实体');
      return false;
    }
    setImportLoading(true);
    try {
      const response = await masterDataRecordApi.import(selectedEntityId, file);
      if (response.code === 200) {
        message.success(`导入成功：成功 ${response.data.successCount} 条，失败 ${response.data.failCount} 条`);
        if (response.data.failCount > 0) {
          message.warning(`失败原因：${response.data.errors.join(', ')}`);
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
      message.warning('请先选择一个实体');
      return;
    }
    try {
      const blob = await masterDataRecordApi.export(selectedEntityId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `master-data-${selectedEntityId}-${new Date().getTime()}.xlsx`;
      a.click();
      window.URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch (error) {
      message.error('导出失败');
    }
  };

  // 状态标签
  const getStatusTag = (status: string) => {
    switch (status) {
    case 'DRAFT':
      return <Tag color="blue">草稿</Tag>;
    case 'PUBLISHED':
      return <Tag color="green">已发布</Tag>;
    case 'ARCHIVED':
      return <Tag color="gray">已归档</Tag>;
    default:
      return <Tag>{status}</Tag>;
    }
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
        formItem = <Input type="number" placeholder={`请输入${field.name}`} />;
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
          name={field.name}
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
        title: '记录ID',
        dataIndex: 'id',
        key: 'id'
      }
    ];

    fields.forEach(field => {
      columns.push({
        title: field.name,
        dataIndex: `data.${field.name}`,
        key: field.name,
        ellipsis: true
      });
    });

    columns.push(
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
            {record.status === 'DRAFT' && (
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
          </Space>
        )
      }
    );

    return columns;
  };

  return (
    <div style={{ padding: '20px' }}>
      <Card title="主数据记录管理">
        {/* 实体选择和操作按钮 */}
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
          <Form.Item label="状态">
            <Select
              style={{ width: 150 }}
              placeholder="请选择状态"
              value={selectedStatus || undefined}
              onChange={setSelectedStatus}
            >
              <Option value="">全部</Option>
              <Option value="DRAFT">草稿</Option>
              <Option value="PUBLISHED">已发布</Option>
              <Option value="ARCHIVED">已归档</Option>
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
          pagination={{
            total,
            pageSize,
            current: page,
            onChange: (current, size) => {
              setPage(current);
              setPageSize(size);
            }
          }}
          locale={{ emptyText: '请先选择一个实体' }}
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
              {fields.map(field => (
                <Descriptions.Item key={field.id} label={field.name}>
                  {String(currentRecord.data[field.name] ?? '-')}
                </Descriptions.Item>
              ))}
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
    </div>
  );
};

export default RecordManagement;
