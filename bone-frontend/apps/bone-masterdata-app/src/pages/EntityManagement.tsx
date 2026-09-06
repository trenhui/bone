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
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, RocketOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  MasterDataEntity,
  CreateMasterDataEntityReq,
  MasterDataEntityPageQry
} from '../types';
import { masterDataEntityApi } from '../services/api';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

const EntityManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [currentEntity, setCurrentEntity] = useState<MasterDataEntity | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MasterDataEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchParams, setSearchParams] = useState<MasterDataEntityPageQry>({});

  const fetchEntities = useCallback(async () => {
    setLoading(true);
    try {
      const response = await masterDataEntityApi.page({
        ...searchParams,
        pageNum: page,
        pageSize: pageSize
      });
      if (response.code === 200) {
        setData(response.data.list);
        setTotal(response.data.total);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取实体列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, searchParams]);

  useEffect(() => {
    void fetchEntities();
  }, [fetchEntities]);

  // 打开创建模态框
  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentEntity(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  // 打开编辑模态框
  const handleEdit = (record: MasterDataEntity) => {
    setIsEditMode(true);
    setCurrentEntity(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      category: record.category
    });
    setIsModalOpen(true);
  };

  // 打开查看模态框
  const handleView = (record: MasterDataEntity) => {
    setCurrentEntity(record);
    setIsViewModalOpen(true);
  };

  // 删除实体
  const handleDelete = async (id: number) => {
    try {
      const response = await masterDataEntityApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  // 发布实体
  const handlePublish = async (id: number) => {
    try {
      const response = await masterDataEntityApi.publish(id);
      if (response.code === 200) {
        message.success('发布成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('发布失败');
    }
  };

  // 停用实体
  const handleDisable = async (id: number) => {
    try {
      const response = await masterDataEntityApi.disable(id);
      if (response.code === 200) {
        message.success('停用成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('停用失败');
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      let response;
      if (isEditMode && currentEntity) {
        response = await masterDataEntityApi.update(currentEntity.id, values);
      } else {
        response = await masterDataEntityApi.create(values as CreateMasterDataEntityReq);
      }
      if (response.code === 200) {
        message.success(isEditMode ? '更新成功' : '创建成功');
        setIsModalOpen(false);
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 状态标签
  const getStatusTag = (status: string) => {
    switch (status) {
    case 'DRAFT':
      return <Tag color="blue">草稿</Tag>;
    case 'PUBLISHED':
      return <Tag color="green">已发布</Tag>;
    default:
      return <Tag>{status}</Tag>;
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '实体名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: MasterDataEntity) => (
        <a onClick={() => handleView(record)}>{text}</a>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category'
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
      render: (_: unknown, record: MasterDataEntity) => (
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
              icon={<RocketOutlined />}
              onClick={() => handlePublish(record.id)}
            >
              发布
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button
              icon={<CloseCircleOutlined />}
              onClick={() => handleDisable(record.id)}
            >
              停用
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: '20px' }}>
      <Card title="主数据实体管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>创建实体</Button>}>
        {/* 搜索表单 */}
        <Form
          layout="inline"
          style={{ marginBottom: 16 }}
          onFinish={(values) => {
            setSearchParams(values);
            setPage(1);
          }}
        >
          <Form.Item name="name" label="实体名称">
            <Input placeholder="请输入实体名称" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="请输入分类" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态">
              <Option value="DRAFT">草稿</Option>
              <Option value="PUBLISHED">已发布</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit">搜索</Button>
          </Form.Item>
          <Form.Item>
            <Button onClick={() => setSearchParams({})}>重置</Button>
          </Form.Item>
        </Form>

        {/* 实体列表 */}
        <ProTable
          options={false}
          columns={columns as ProColumns<MasterDataEntity>[]}
          dataSource={data}
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
        />
      </Card>

      {/* 创建/编辑模态框 */}
      <Modal
        title={isEditMode ? '编辑实体' : '创建实体'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="实体名称"
            rules={[{ required: true, message: '请输入实体名称' }]}
          >
            <Input placeholder="请输入实体名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} placeholder="请输入实体描述" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="请输入分类" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看详情模态框 */}
      <Modal
        title="实体详情"
        open={isViewModalOpen}
        onCancel={() => setIsViewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsViewModalOpen(false)}>关闭</Button>
        ]}
      >
        {currentEntity && (
          <Descriptions column={2}>
            <Descriptions.Item label="实体名称">{currentEntity.name}</Descriptions.Item>
            <Descriptions.Item label="分类">{currentEntity.category || '-'}</Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{currentEntity.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{getStatusTag(currentEntity.status)}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentEntity.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间" span={2}>{currentEntity.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default EntityManagement;
